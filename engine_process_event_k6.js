import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

// modes:
// 1) TEST_MODE=baseline  -> single endpoint, constant arrival rate
// 2) TEST_MODE=compare   -> async/sync compare, each takes half rate
// 3) TEST_MODE=max       -> ramping arrival rate to find limit QPS

const TEST_MODE = __ENV.TEST_MODE || 'baseline';
const TARGET_MODE = __ENV.TARGET_MODE || 'async'; // async|sync|direct|noop
const RATE = Number(__ENV.RATE || 200);
const PRE_VUS = Number(__ENV.PRE_VUS || 800);
const MAX_VUS = Number(__ENV.MAX_VUS || 12000);
const DURATION = __ENV.DURATION || '3m';
const START_RATE = Number(__ENV.START_RATE || 7000);
const WINDOW_SEC = Math.max(1, Number(__ENV.WINDOW_SEC || 10));
const MAX_STAGES_JSON = __ENV.MAX_STAGES_JSON || '[{"start":7500,"target":7800,"duration":"1m"}]';
const EXTREME_QPS_DEFINITION = 'max achieved_qps among stable windows where injection_ratio>=min_injection_ratio, biz_success_rate>=min_biz_success_rate and p95<=max_p95_ms';

const TURNING_INJECT_RATIO = Number(__ENV.TURNING_INJECT_RATIO || 0.95);
const TURNING_BIZ_SUCCESS_RATE = Number(__ENV.TURNING_BIZ_SUCCESS_RATE || 0.995);
const TURNING_P95_MS = Number(__ENV.TURNING_P95_MS || 500);

const ASYNC_BIZ_SUCCESS_CODES = String(__ENV.ASYNC_BIZ_SUCCESS_CODES || '200,202')
  .split(',')
  .map((s) => Number(s.trim()))
  .filter((n) => Number.isFinite(n));
const ASYNC_ACCEPTED_CODES = String(__ENV.ASYNC_ACCEPTED_CODES || '200,202,503')
  .split(',')
  .map((s) => Number(s.trim()))
  .filter((n) => Number.isFinite(n));

const biz_success_reqs = new Counter('biz_success_reqs');
const biz_accepted_reqs = new Counter('biz_accepted_reqs');
const biz_fail_reqs = new Counter('biz_fail_reqs');
const biz_degraded_reqs = new Counter('biz_degraded_reqs');
const status_503_reqs = new Counter('status_503_reqs');
const biz_success_rate = new Rate('biz_success_rate');
const biz_accepted_rate = new Rate('biz_accepted_rate');
const stage_reqs = new Counter('stage_reqs');
const stage_biz_success_reqs = new Counter('stage_biz_success_reqs');
const stage_accepted_reqs = new Counter('stage_accepted_reqs');
const stage_fail_reqs = new Counter('stage_fail_reqs');
const stage_req_duration = new Trend('stage_req_duration', true);
const window_reqs = new Counter('window_reqs');
const window_biz_success_reqs = new Counter('window_biz_success_reqs');
const window_accepted_reqs = new Counter('window_accepted_reqs');
const window_fail_reqs = new Counter('window_fail_reqs');
const window_req_duration = new Trend('window_req_duration', true);

function parseStages() {
  const defaults = [
    { start: 7000, target: 8000, duration: '1m' },
    { start: 8000, target: 9000, duration: '1m' },
    { start: 9000, target: 10000, duration: '1m' },
    { start: 10000, target: 11000, duration: '1m' },
    { start: 11000, target: 12000, duration: '1m' },
  ];
  let parsed = defaults;
  try {
    const fromEnv = JSON.parse(MAX_STAGES_JSON);
    if (Array.isArray(fromEnv) && fromEnv.length > 0) {
      parsed = fromEnv;
    }
  } catch (_) {}

  const normalized = [];
  let prevTarget = START_RATE > 0 ? START_RATE : 7000;
  for (let i = 0; i < parsed.length; i++) {
    const src = parsed[i] || {};
    const target = Number(src.target);
    if (!Number.isFinite(target) || target <= 0) continue;
    const startFromStage = Number(src.start);
    const start = Number.isFinite(startFromStage) && startFromStage > 0 ? startFromStage : prevTarget;
    const duration = typeof src.duration === 'string' && src.duration.trim() ? src.duration.trim() : '1m';
    normalized.push({ start, target, duration });
    prevTarget = target;
  }

  if (normalized.length === 0) {
    return defaults;
  }
  return normalized;
}

function parseDurationToSec(duration) {
  if (!duration || typeof duration !== 'string') return 0;
  const m = /^\s*(\d+(?:\.\d+)?)\s*([smh])\s*$/i.exec(duration);
  if (!m) return 0;
  const value = Number(m[1]);
  const unit = m[2].toLowerCase();
  if (unit === 's') return value;
  if (unit === 'm') return value * 60;
  return value * 3600;
}

function stageWindowMeta(stage, inStageElapsedSec) {
  const stageDurationSec = Math.max(1, parseDurationToSec(stage.duration));
  const elapsed = Math.max(0, Math.min(stageDurationSec, inStageElapsedSec));
  const windowCount = Math.max(1, Math.ceil(stageDurationSec / WINDOW_SEC));
  const windowIndex = Math.min(windowCount, Math.floor(elapsed / WINDOW_SEC) + 1);
  const windowStartSec = (windowIndex - 1) * WINDOW_SEC;
  const windowDurationSec = windowIndex === windowCount
    ? Math.max(1, stageDurationSec - windowStartSec)
    : Math.max(1, Math.min(WINDOW_SEC, stageDurationSec));
  const midSec = Math.min(stageDurationSec, windowStartSec + windowDurationSec / 2);
  const ratio = stageDurationSec > 0 ? midSec / stageDurationSec : 1;
  const windowTarget = stage.start + (stage.target - stage.start) * ratio;
  return {
    windowIndex,
    windowDurationSec,
    windowTarget,
  };
}

const STAGES = parseStages();

function buildScenarios() {
  if (TEST_MODE === 'compare') {
    const half = Math.max(1, Math.floor(RATE / 2));
    return {
      compare_async: {
        executor: 'constant-arrival-rate',
        rate: half,
        timeUnit: '1s',
        duration: DURATION,
        preAllocatedVUs: PRE_VUS,
        maxVUs: MAX_VUS,
      },
      compare_sync: {
        executor: 'constant-arrival-rate',
        rate: half,
        timeUnit: '1s',
        duration: DURATION,
        preAllocatedVUs: PRE_VUS,
        maxVUs: MAX_VUS,
      },
    };
  }
  if (TEST_MODE === 'max') {
    return {
      max_probe: {
        executor: 'ramping-arrival-rate',
        startRate: STAGES[0] ? STAGES[0].start : START_RATE,
        timeUnit: '1s',
        preAllocatedVUs: PRE_VUS,
        maxVUs: MAX_VUS,
        stages: STAGES.map((s) => ({ target: s.target, duration: s.duration })),
      },
    };
  }
  return {
    baseline_single: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
    },
  };
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1200'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
};

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const BEARER_TOKEN = __ENV.BEARER_TOKEN || '';
const DUPLICATE_RATE = Number(__ENV.DUPLICATE_RATE || 0.1);
const NO_MSG_ID_RATE = Number(__ENV.NO_MSG_ID_RATE || 0.03);
const USER_IDS = [2000000000000000000,2000000000000000001,2000000000000000002,2000000000000000003,2000000000000000004,2000000000000000005,2000000000000000006,2000000000000000007,2000000000000000008,2000000000000000009,2000000000000000010,2000000000000000011,2000000000000000012,2000000000000000013,2000000000000000014,2000000000000000015,2000000000000000016,2000000000000000017,2000000000000000018,2000000000000000019,2000000000000000020,2000000000000000021,2000000000000000022,2000000000000000023,2000000000000000024,2000000000000000025,2000000000000000026,2000000000000000027,2000000000000000028,2000000000000000029,2000000000000000030,2000000000000000031,2000000000000000032,2000000000000000033,2000000000000000034,2000000000000000035,2000000000000000036,2000000000000000037,2000000000000000038,2000000000000000039,2000000000000000040,2000000000000000041,2000000000000000042,2000000000000000043,2000000000000000044,2000000000000000045,2000000000000000046,2000000000000000047,2000000000000000048,2000000000000000049,2000000000000000050,2000000000000000051,2000000000000000052,2000000000000000053,2000000000000000054,2000000000000000055,2000000000000000056,2000000000000000057,2000000000000000058,2000000000000000059,2000000000000000060,2000000000000000061,2000000000000000062,2000000000000000063,2000000000000000064,2000000000000000065,2000000000000000066,2000000000000000067,2000000000000000068,2000000000000000069,2000000000000000070,2000000000000000071,2000000000000000072,2000000000000000073,2000000000000000074,2000000000000000075,2000000000000000076,2000000000000000077,2000000000000000078,2000000000000000079,2000000000000000080,2000000000000000081,2000000000000000082,2000000000000000083,2000000000000000084,2000000000000000085,2000000000000000086,2000000000000000087,2000000000000000088,2000000000000000089,2000000000000000090,2000000000000000091,2000000000000000092,2000000000000000093,2000000000000000094,2000000000000000095,2000000000000000096,2000000000000000097,2000000000000000098,2000000000000000099,2000000000000000100,2000000000000000101,2000000000000000102,2000000000000000103,2000000000000000104,2000000000000000105,2000000000000000106,2000000000000000107,2000000000000000108,2000000000000000109,2000000000000000110,2000000000000000111,2000000000000000112,2000000000000000113,2000000000000000114,2000000000000000115,2000000000000000116,2000000000000000117,2000000000000000118,2000000000000000119,2000000000000000120,2000000000000000121,2000000000000000122,2000000000000000123,2000000000000000124,2000000000000000125,2000000000000000126,2000000000000000127,2000000000000000128,2000000000000000129,2000000000000000130,2000000000000000131,2000000000000000132,2000000000000000133,2000000000000000134,2000000000000000135,2000000000000000136,2000000000000000137,2000000000000000138,2000000000000000139,2000000000000000140,2000000000000000141,2000000000000000142,2000000000000000143,2000000000000000144,2000000000000000145,2000000000000000146,2000000000000000147,2000000000000000148,2000000000000000149,2000000000000000150,2000000000000000151,2000000000000000152,2000000000000000153,2000000000000000154,2000000000000000155,2000000000000000156,2000000000000000157,2000000000000000158,2000000000000000159,2000000000000000160,2000000000000000161,2000000000000000162,2000000000000000163,2000000000000000164,2000000000000000165,2000000000000000166,2000000000000000167,2000000000000000168,2000000000000000169,2000000000000000170,2000000000000000171,2000000000000000172,2000000000000000173,2000000000000000174,2000000000000000175,2000000000000000176,2000000000000000177,2000000000000000178,2000000000000000179,2000000000000000180,2000000000000000181,2000000000000000182,2000000000000000183,2000000000000000184,2000000000000000185,2000000000000000186,2000000000000000187,2000000000000000188,2000000000000000189,2000000000000000190,2000000000000000191,2000000000000000192,2000000000000000193,2000000000000000194,2000000000000000195,2000000000000000196,2000000000000000197,2000000000000000198,2000000000000000199,2000000000000000200,2000000000000000201,2000000000000000202,2000000000000000203,2000000000000000204,2000000000000000205,2000000000000000206,2000000000000000207,2000000000000000208,2000000000000000209,2000000000000000210,2000000000000000211,2000000000000000212,2000000000000000213,2000000000000000214,2000000000000000215,2000000000000000216,2000000000000000217,2000000000000000218,2000000000000000219,2000000000000000220,2000000000000000221,2000000000000000222,2000000000000000223,2000000000000000224,2000000000000000225,2000000000000000226,2000000000000000227,2000000000000000228,2000000000000000229,2000000000000000230,2000000000000000231,2000000000000000232,2000000000000000233,2000000000000000234,2000000000000000235,2000000000000000236,2000000000000000237,2000000000000000238,2000000000000000239,2000000000000000240,2000000000000000241,2000000000000000242,2000000000000000243,2000000000000000244,2000000000000000245,2000000000000000246,2000000000000000247,2000000000000000248,2000000000000000249,2000000000000000250,2000000000000000251,2000000000000000252,2000000000000000253,2000000000000000254,2000000000000000255,2000000000000000256,2000000000000000257,2000000000000000258,2000000000000000259,2000000000000000260,2000000000000000261,2000000000000000262,2000000000000000263,2000000000000000264,2000000000000000265,2000000000000000266,2000000000000000267,2000000000000000268,2000000000000000269,2000000000000000270,2000000000000000271,2000000000000000272,2000000000000000273,2000000000000000274,2000000000000000275,2000000000000000276,2000000000000000277,2000000000000000278,2000000000000000279,2000000000000000280,2000000000000000281,2000000000000000282,2000000000000000283,2000000000000000284,2000000000000000285,2000000000000000286,2000000000000000287,2000000000000000288,2000000000000000289,2000000000000000290,2000000000000000291,2000000000000000292,2000000000000000293,2000000000000000294,2000000000000000295,2000000000000000296,2000000000000000297,2000000000000000298,2000000000000000299,2000000000000000300,2000000000000000301,2000000000000000302,2000000000000000303,2000000000000000304,2000000000000000305,2000000000000000306,2000000000000000307,2000000000000000308,2000000000000000309,2000000000000000310,2000000000000000311,2000000000000000312,2000000000000000313,2000000000000000314,2000000000000000315,2000000000000000316,2000000000000000317,2000000000000000318,2000000000000000319,2000000000000000320,2000000000000000321,2000000000000000322,2000000000000000323,2000000000000000324,2000000000000000325,2000000000000000326,2000000000000000327,2000000000000000328,2000000000000000329,2000000000000000330,2000000000000000331,2000000000000000332,2000000000000000333,2000000000000000334,2000000000000000335,2000000000000000336,2000000000000000337,2000000000000000338,2000000000000000339,2000000000000000340,2000000000000000341,2000000000000000342,2000000000000000343,2000000000000000344,2000000000000000345,2000000000000000346,2000000000000000347,2000000000000000348,2000000000000000349,2000000000000000350,2000000000000000351,2000000000000000352,2000000000000000353,2000000000000000354,2000000000000000355,2000000000000000356,2000000000000000357,2000000000000000358,2000000000000000359,2000000000000000360,2000000000000000361,2000000000000000362,2000000000000000363,2000000000000000364,2000000000000000365,2000000000000000366,2000000000000000367,2000000000000000368,2000000000000000369,2000000000000000370,2000000000000000371,2000000000000000372,2000000000000000373,2000000000000000374,2000000000000000375,2000000000000000376,2000000000000000377,2000000000000000378,2000000000000000379,2000000000000000380,2000000000000000381,2000000000000000382,2000000000000000383,2000000000000000384,2000000000000000385,2000000000000000386,2000000000000000387,2000000000000000388,2000000000000000389,2000000000000000390,2000000000000000391,2000000000000000392,2000000000000000393,2000000000000000394,2000000000000000395,2000000000000000396,2000000000000000397,2000000000000000398,2000000000000000399,2000000000000000400,2000000000000000401,2000000000000000402,2000000000000000403,2000000000000000404,2000000000000000405,2000000000000000406,2000000000000000407,2000000000000000408,2000000000000000409,2000000000000000410,2000000000000000411,2000000000000000412,2000000000000000413,2000000000000000414,2000000000000000415,2000000000000000416,2000000000000000417,2000000000000000418,2000000000000000419,2000000000000000420,2000000000000000421,2000000000000000422,2000000000000000423,2000000000000000424,2000000000000000425,2000000000000000426,2000000000000000427,2000000000000000428,2000000000000000429,2000000000000000430,2000000000000000431,2000000000000000432,2000000000000000433,2000000000000000434,2000000000000000435,2000000000000000436,2000000000000000437,2000000000000000438,2000000000000000439,2000000000000000440,2000000000000000441,2000000000000000442,2000000000000000443,2000000000000000444,2000000000000000445,2000000000000000446,2000000000000000447,2000000000000000448,2000000000000000449,2000000000000000450,2000000000000000451,2000000000000000452,2000000000000000453,2000000000000000454,2000000000000000455,2000000000000000456,2000000000000000457,2000000000000000458,2000000000000000459,2000000000000000460,2000000000000000461,2000000000000000462,2000000000000000463,2000000000000000464,2000000000000000465,2000000000000000466,2000000000000000467,2000000000000000468,2000000000000000469,2000000000000000470,2000000000000000471,2000000000000000472,2000000000000000473,2000000000000000474,2000000000000000475,2000000000000000476,2000000000000000477,2000000000000000478,2000000000000000479,2000000000000000480,2000000000000000481,2000000000000000482,2000000000000000483,2000000000000000484,2000000000000000485,2000000000000000486,2000000000000000487,2000000000000000488,2000000000000000489,2000000000000000490,2000000000000000491,2000000000000000492,2000000000000000493,2000000000000000494,2000000000000000495,2000000000000000496,2000000000000000497,2000000000000000498,2000000000000000499,2000000000000000500,2000000000000000501,2000000000000000502,2000000000000000503,2000000000000000504,2000000000000000505,2000000000000000506,2000000000000000507,2000000000000000508,2000000000000000509,2000000000000000510,2000000000000000511,2000000000000000512,2000000000000000513,2000000000000000514,2000000000000000515,2000000000000000516,2000000000000000517,2000000000000000518,2000000000000000519,2000000000000000520,2000000000000000521,2000000000000000522,2000000000000000523,2000000000000000524,2000000000000000525,2000000000000000526,2000000000000000527,2000000000000000528,2000000000000000529,2000000000000000530,2000000000000000531,2000000000000000532,2000000000000000533,2000000000000000534,2000000000000000535,2000000000000000536,2000000000000000537,2000000000000000538,2000000000000000539,2000000000000000540,2000000000000000541,2000000000000000542,2000000000000000543,2000000000000000544,2000000000000000545,2000000000000000546,2000000000000000547,2000000000000000548,2000000000000000549,2000000000000000550,2000000000000000551,2000000000000000552,2000000000000000553,2000000000000000554,2000000000000000555,2000000000000000556,2000000000000000557,2000000000000000558,2000000000000000559,2000000000000000560,2000000000000000561,2000000000000000562,2000000000000000563,2000000000000000564,2000000000000000565,2000000000000000566,2000000000000000567,2000000000000000568,2000000000000000569,2000000000000000570,2000000000000000571,2000000000000000572,2000000000000000573,2000000000000000574,2000000000000000575,2000000000000000576,2000000000000000577,2000000000000000578,2000000000000000579,2000000000000000580,2000000000000000581,2000000000000000582,2000000000000000583,2000000000000000584,2000000000000000585,2000000000000000586,2000000000000000587,2000000000000000588,2000000000000000589,2000000000000000590,2000000000000000591,2000000000000000592,2000000000000000593,2000000000000000594,2000000000000000595,2000000000000000596,2000000000000000597,2000000000000000598,2000000000000000599,2000000000000000600,2000000000000000601,2000000000000000602,2000000000000000603,2000000000000000604,2000000000000000605,2000000000000000606,2000000000000000607,2000000000000000608,2000000000000000609,2000000000000000610,2000000000000000611,2000000000000000612,2000000000000000613,2000000000000000614,2000000000000000615,2000000000000000616,2000000000000000617,2000000000000000618,2000000000000000619,2000000000000000620,2000000000000000621,2000000000000000622,2000000000000000623,2000000000000000624,2000000000000000625,2000000000000000626,2000000000000000627,2000000000000000628,2000000000000000629,2000000000000000630,2000000000000000631,2000000000000000632,2000000000000000633,2000000000000000634,2000000000000000635,2000000000000000636,2000000000000000637,2000000000000000638,2000000000000000639,2000000000000000640,2000000000000000641,2000000000000000642,2000000000000000643,2000000000000000644,2000000000000000645,2000000000000000646,2000000000000000647,2000000000000000648,2000000000000000649,2000000000000000650,2000000000000000651,2000000000000000652,2000000000000000653,2000000000000000654,2000000000000000655,2000000000000000656,2000000000000000657,2000000000000000658,2000000000000000659,2000000000000000660,2000000000000000661,2000000000000000662,2000000000000000663,2000000000000000664,2000000000000000665,2000000000000000666,2000000000000000667,2000000000000000668,2000000000000000669,2000000000000000670,2000000000000000671,2000000000000000672,2000000000000000673,2000000000000000674,2000000000000000675,2000000000000000676,2000000000000000677,2000000000000000678,2000000000000000679,2000000000000000680,2000000000000000681,2000000000000000682,2000000000000000683,2000000000000000684,2000000000000000685,2000000000000000686,2000000000000000687,2000000000000000688,2000000000000000689,2000000000000000690,2000000000000000691,2000000000000000692,2000000000000000693,2000000000000000694,2000000000000000695,2000000000000000696,2000000000000000697,2000000000000000698,2000000000000000699,2000000000000000700,2000000000000000701,2000000000000000702,2000000000000000703,2000000000000000704,2000000000000000705,2000000000000000706,2000000000000000707,2000000000000000708,2000000000000000709,2000000000000000710,2000000000000000711,2000000000000000712,2000000000000000713,2000000000000000714,2000000000000000715,2000000000000000716,2000000000000000717,2000000000000000718,2000000000000000719,2000000000000000720,2000000000000000721,2000000000000000722,2000000000000000723,2000000000000000724,2000000000000000725,2000000000000000726,2000000000000000727,2000000000000000728,2000000000000000729,2000000000000000730,2000000000000000731,2000000000000000732,2000000000000000733,2000000000000000734,2000000000000000735,2000000000000000736,2000000000000000737,2000000000000000738,2000000000000000739,2000000000000000740,2000000000000000741,2000000000000000742,2000000000000000743,2000000000000000744,2000000000000000745,2000000000000000746,2000000000000000747,2000000000000000748,2000000000000000749,2000000000000000750,2000000000000000751,2000000000000000752,2000000000000000753,2000000000000000754,2000000000000000755,2000000000000000756,2000000000000000757,2000000000000000758,2000000000000000759,2000000000000000760,2000000000000000761,2000000000000000762,2000000000000000763,2000000000000000764,2000000000000000765,2000000000000000766,2000000000000000767,2000000000000000768,2000000000000000769,2000000000000000770,2000000000000000771,2000000000000000772,2000000000000000773,2000000000000000774,2000000000000000775,2000000000000000776,2000000000000000777,2000000000000000778,2000000000000000779,2000000000000000780,2000000000000000781,2000000000000000782,2000000000000000783,2000000000000000784,2000000000000000785,2000000000000000786,2000000000000000787,2000000000000000788,2000000000000000789,2000000000000000790,2000000000000000791,2000000000000000792,2000000000000000793,2000000000000000794,2000000000000000795,2000000000000000796,2000000000000000797,2000000000000000798,2000000000000000799,2000000000000000800,2000000000000000801,2000000000000000802,2000000000000000803,2000000000000000804,2000000000000000805,2000000000000000806,2000000000000000807,2000000000000000808,2000000000000000809,2000000000000000810,2000000000000000811,2000000000000000812,2000000000000000813,2000000000000000814,2000000000000000815,2000000000000000816,2000000000000000817,2000000000000000818,2000000000000000819,2000000000000000820,2000000000000000821,2000000000000000822,2000000000000000823,2000000000000000824,2000000000000000825,2000000000000000826,2000000000000000827,2000000000000000828,2000000000000000829,2000000000000000830,2000000000000000831,2000000000000000832,2000000000000000833,2000000000000000834,2000000000000000835,2000000000000000836,2000000000000000837,2000000000000000838,2000000000000000839,2000000000000000840,2000000000000000841,2000000000000000842,2000000000000000843,2000000000000000844,2000000000000000845,2000000000000000846,2000000000000000847,2000000000000000848,2000000000000000849,2000000000000000851];
const EVENT_TYPES = ['USER_LEARN', 'USER_SIGN'];
const DUP_POOL = Array.from({ length: 200 }, (_, i) => `dup-${i + 1}`);
const ASYNC_ENDPOINT = '/api/engine/process-event-async';
const SYNC_ENDPOINT = '/api/engine/process-event-sync';
const DIRECT_ENDPOINT = '/api/engine/process-event-direct';
const NOOP_ENDPOINT = '/api/benchmark/noop';
const RUN_ID = __ENV.RUN_ID || `${Date.now()}-${Math.floor(Math.random() * 1000000)}`;

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function endpointTypeForScenario() {
  if (TEST_MODE === 'compare') {
    return exec.scenario.name && exec.scenario.name.indexOf('sync') >= 0 ? 'sync' : 'async';
  }
  if (TARGET_MODE === 'noop') {
    return 'noop';
  }
  if (TARGET_MODE === 'sync') {
    return 'sync';
  }
  if (TARGET_MODE === 'direct') {
    return 'direct';
  }
  return 'async';
}

function isDirectBizSuccess(res, statusCode) {
  if (statusCode !== 200) {
    return false;
  }
  if (!res || !res.body) {
    return false;
  }
  // direct endpoint returns { data: { status: "processed_direct" } } on success
  return String(res.body).indexOf('processed_direct') >= 0;
}

function stageMeta() {
  if (TEST_MODE !== 'max') {
    return {
      stageIndex: 'na',
      stageStart: 'na',
      stageTarget: 'na',
      stageDurationSec: 0,
      windowIndex: 'na',
      windowTarget: 'na',
      windowDurationSec: 0,
    };
  }
  const elapsedSec = exec.instance.currentTestRunDuration / 1000;
  let acc = 0;
  for (let i = 0; i < STAGES.length; i++) {
    const stage = STAGES[i];
    const sec = Math.max(1, parseDurationToSec(stage.duration));
    const stageStartAt = acc;
    acc += sec;
    if (elapsedSec <= acc || i === STAGES.length - 1) {
      const inStageElapsedSec = Math.max(0, elapsedSec - stageStartAt);
      const windowMeta = stageWindowMeta(stage, inStageElapsedSec);
      return {
        stageIndex: String(i + 1),
        stageStart: String(stage.start),
        stageTarget: String(stage.target),
        stageDurationSec: sec,
        windowIndex: String(windowMeta.windowIndex),
        windowTarget: String(windowMeta.windowTarget),
        windowDurationSec: windowMeta.windowDurationSec,
      };
    }
  }
  const last = STAGES[STAGES.length - 1] || { start: START_RATE, target: START_RATE, duration: '1m' };
  const fallbackWindow = stageWindowMeta(last, parseDurationToSec(last.duration));
  return {
    stageIndex: String(STAGES.length || 1),
    stageStart: String(last.start),
    stageTarget: String(last.target),
    stageDurationSec: Math.max(1, parseDurationToSec(last.duration)),
    windowIndex: String(fallbackWindow.windowIndex),
    windowTarget: String(fallbackWindow.windowTarget),
    windowDurationSec: fallbackWindow.windowDurationSec,
  };
}

function parseTags(text) {
  const tags = {};
  if (!text) return tags;
  text.split(',').forEach((part) => {
    const idx = part.indexOf(':');
    if (idx > 0) {
      const k = part.slice(0, idx).trim();
      const v = part.slice(idx + 1).trim();
      tags[k] = v;
    }
  });
  return tags;
}

function getCount(metric) {
  if (!metric || !metric.values || typeof metric.values.count !== 'number') return 0;
  return metric.values.count;
}

function getRate(metric) {
  if (!metric || !metric.values || typeof metric.values.rate !== 'number') return 0;
  return metric.values.rate;
}

function getP95(metric) {
  if (!metric || !metric.values || typeof metric.values['p(95)'] !== 'number') return 0;
  return metric.values['p(95)'];
}

export default function () {
  const userId = USER_IDS[(__VU + __ITER) % USER_IDS.length];
  const endpointType = endpointTypeForScenario();
  const endpoint = endpointType === 'sync'
    ? SYNC_ENDPOINT
    : endpointType === 'direct'
      ? DIRECT_ENDPOINT
    : endpointType === 'noop'
      ? NOOP_ENDPOINT
      : ASYNC_ENDPOINT;
  const useDuplicate = Math.random() < DUPLICATE_RATE;
  const dropMessageId = Math.random() < NO_MSG_ID_RATE;
  const uniqueId = `mid-${Date.now()}-${__VU}-${__ITER}-${Math.floor(Math.random() * 100000)}`;
  const requestId = `req-${RUN_ID}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;
  const messageId = useDuplicate ? pick(DUP_POOL) : uniqueId;

  const payload = {
    userId: userId,
    eventType: pick(EVENT_TYPES),
    value: Math.floor(Math.random() * 5) + 1,
    time: new Date().toISOString(),
    requestId: requestId,
    eventId: `evt-${uniqueId}`,
    deviceId: `device-${(userId % 500) + 1}`,
    ip: `10.10.${userId % 20}.${(userId % 200) + 1}`,
    channel: 'k6',
  };
  if (!dropMessageId) {
    payload.messageId = messageId;
  }

  const headers = { 'Content-Type': 'application/json' };
  if (BEARER_TOKEN) {
    headers.Authorization = `Bearer ${BEARER_TOKEN}`;
  }

  const meta = stageMeta();
  const tags = {
    endpoint_type: endpointType,
    scenario_name: exec.scenario.name || 'na',
    stage_index: meta.stageIndex,
    stage_start: meta.stageStart,
    stage_target: meta.stageTarget,
    window_index: meta.windowIndex,
    window_target: meta.windowTarget,
  };

  const reqOptions = { headers, tags };
  const res = endpointType === 'noop'
    ? http.get(`${BASE_URL}${endpoint}`, reqOptions)
    : http.post(`${BASE_URL}${endpoint}`, JSON.stringify(payload), reqOptions);

  const statusCode = Number(res.status || 0);
  const accepted = endpointType === 'async'
    ? ASYNC_ACCEPTED_CODES.includes(statusCode)
    : statusCode === 200;
  const bizSuccess = endpointType === 'async'
    ? ASYNC_BIZ_SUCCESS_CODES.includes(statusCode)
    : endpointType === 'direct'
      ? isDirectBizSuccess(res, statusCode)
      : statusCode === 200;
  const degraded = accepted && !bizSuccess;

  check(res, {
    status_ok: () => accepted,
  });

  biz_success_reqs.add(bizSuccess ? 1 : 0, tags);
  biz_accepted_reqs.add(accepted ? 1 : 0, tags);
  biz_fail_reqs.add(accepted ? 0 : 1, tags);
  biz_degraded_reqs.add(degraded ? 1 : 0, tags);
  biz_success_rate.add(bizSuccess, tags);
  biz_accepted_rate.add(accepted, tags);
  if (statusCode === 503) {
    status_503_reqs.add(1, tags);
  }

  stage_reqs.add(1, tags);
  stage_biz_success_reqs.add(bizSuccess ? 1 : 0, tags);
  stage_accepted_reqs.add(accepted ? 1 : 0, tags);
  stage_fail_reqs.add(accepted ? 0 : 1, tags);
  stage_req_duration.add(res.timings.duration, tags);

  window_reqs.add(1, tags);
  window_biz_success_reqs.add(bizSuccess ? 1 : 0, tags);
  window_accepted_reqs.add(accepted ? 1 : 0, tags);
  window_fail_reqs.add(accepted ? 0 : 1, tags);
  window_req_duration.add(res.timings.duration, tags);

  sleep(Number(__ENV.SLEEP_SEC || 0));
}

export function handleSummary(data) {
  const metrics = data.metrics || {};
  const totalDurationSec = (data.state && data.state.testRunDurationMs ? data.state.testRunDurationMs : 0) / 1000;
  const totalReqCount = getCount(metrics.http_reqs);
  const totalReqRate = getRate(metrics.http_reqs);
  const bizSuccessCount = getCount(metrics.biz_success_reqs);
  const bizAcceptedCount = getCount(metrics.biz_accepted_reqs);
  const bizFailCount = getCount(metrics.biz_fail_reqs);
  const degradedCount = getCount(metrics.biz_degraded_reqs);
  const status503Count = getCount(metrics.status_503_reqs);

  const bizSuccessQps = totalDurationSec > 0 ? bizSuccessCount / totalDurationSec : 0;
  const bizAcceptedQps = totalDurationSec > 0 ? bizAcceptedCount / totalDurationSec : 0;

  const stages = STAGES;
  const stageDurationSecMap = {};
  const windowSpecMap = {};
  for (let i = 0; i < stages.length; i++) {
    const stage = stages[i];
    const stageIndex = String(i + 1);
    const stageDurationSec = Math.max(1, parseDurationToSec(stage.duration));
    stageDurationSecMap[stageIndex] = stageDurationSec;
    const windowCount = Math.max(1, Math.ceil(stageDurationSec / WINDOW_SEC));
    for (let w = 1; w <= windowCount; w++) {
      const windowStartSec = (w - 1) * WINDOW_SEC;
      const windowDurationSec = w === windowCount
        ? Math.max(1, stageDurationSec - windowStartSec)
        : Math.max(1, Math.min(WINDOW_SEC, stageDurationSec));
      const midSec = Math.min(stageDurationSec, windowStartSec + windowDurationSec / 2);
      const ratio = stageDurationSec > 0 ? midSec / stageDurationSec : 1;
      const windowTarget = stage.start + (stage.target - stage.start) * ratio;
      windowSpecMap[`${stageIndex}-${w}`] = {
        stage_duration_sec: stageDurationSec,
        window_duration_sec: windowDurationSec,
        window_target: windowTarget,
      };
    }
  }

  const stageStatsMap = {};
  const windowStatsMap = {};
  Object.keys(metrics).forEach((name) => {
    const stageMatch = /^(stage_reqs|stage_biz_success_reqs|stage_accepted_reqs|stage_fail_reqs|stage_req_duration)\{(.+)}$/.exec(name);
    if (stageMatch) {
      const metricName = stageMatch[1];
      const tags = parseTags(stageMatch[2]);
      const stageIndex = tags.stage_index;
      if (!stageIndex || stageIndex === 'na') return;
      if (!stageStatsMap[stageIndex]) {
        stageStatsMap[stageIndex] = {
          stage_index: Number(stageIndex),
          stage_start: Number(tags.stage_start || 0),
          stage_target: Number(tags.stage_target || 0),
          stage_duration_sec: stageDurationSecMap[stageIndex] || 0,
          request_count: 0,
          biz_success_count: 0,
          accepted_count: 0,
          fail_count: 0,
          p95_ms: 0,
        };
      }
      const slot = stageStatsMap[stageIndex];
      if (metricName === 'stage_reqs') slot.request_count = getCount(metrics[name]);
      if (metricName === 'stage_biz_success_reqs') slot.biz_success_count = getCount(metrics[name]);
      if (metricName === 'stage_accepted_reqs') slot.accepted_count = getCount(metrics[name]);
      if (metricName === 'stage_fail_reqs') slot.fail_count = getCount(metrics[name]);
      if (metricName === 'stage_req_duration') slot.p95_ms = getP95(metrics[name]);
      return;
    }

    const windowMatch = /^(window_reqs|window_biz_success_reqs|window_accepted_reqs|window_fail_reqs|window_req_duration)\{(.+)}$/.exec(name);
    if (!windowMatch) return;
    const metricName = windowMatch[1];
    const tags = parseTags(windowMatch[2]);
    const stageIndex = tags.stage_index;
    const windowIndex = tags.window_index;
    if (!stageIndex || stageIndex === 'na' || !windowIndex || windowIndex === 'na') return;
    const key = `${stageIndex}-${windowIndex}`;
    if (!windowStatsMap[key]) {
      const spec = windowSpecMap[key] || { window_duration_sec: WINDOW_SEC, window_target: Number(tags.window_target || 0) };
      windowStatsMap[key] = {
        stage_index: Number(stageIndex),
        window_index: Number(windowIndex),
        stage_target: Number(tags.stage_target || 0),
        window_target: spec.window_target,
        window_duration_sec: spec.window_duration_sec,
        request_count: 0,
        biz_success_count: 0,
        accepted_count: 0,
        fail_count: 0,
        p95_ms: 0,
      };
    }
    const slot = windowStatsMap[key];
    if (metricName === 'window_reqs') slot.request_count = getCount(metrics[name]);
    if (metricName === 'window_biz_success_reqs') slot.biz_success_count = getCount(metrics[name]);
    if (metricName === 'window_accepted_reqs') slot.accepted_count = getCount(metrics[name]);
    if (metricName === 'window_fail_reqs') slot.fail_count = getCount(metrics[name]);
    if (metricName === 'window_req_duration') slot.p95_ms = getP95(metrics[name]);
  });

  const stageMetrics = Object.keys(stageStatsMap)
    .sort((a, b) => Number(a) - Number(b))
    .map((key) => {
      const s = stageStatsMap[key];
      const duration = s.stage_duration_sec > 0 ? s.stage_duration_sec : 1;
      const achievedQps = s.request_count / duration;
      const bizSuccessRate = s.request_count > 0 ? s.biz_success_count / s.request_count : 0;
      const acceptedRate = s.request_count > 0 ? s.accepted_count / s.request_count : 0;
      const reachedInject = s.stage_target > 0 ? achievedQps / s.stage_target : 0;
      const stable = reachedInject >= TURNING_INJECT_RATIO
        && bizSuccessRate >= TURNING_BIZ_SUCCESS_RATE
        && s.p95_ms <= TURNING_P95_MS;
      return {
        stage_index: s.stage_index,
        stage_start: s.stage_start,
        stage_target: s.stage_target,
        stage_duration_sec: s.stage_duration_sec,
        achieved_qps: achievedQps,
        injection_ratio: reachedInject,
        biz_success_rate: bizSuccessRate,
        accepted_rate: acceptedRate,
        p95_ms: s.p95_ms,
        stable: stable,
      };
    });

  const windowMetrics = Object.keys(windowStatsMap)
    .sort((a, b) => {
      const [sa, wa] = a.split('-').map((x) => Number(x));
      const [sb, wb] = b.split('-').map((x) => Number(x));
      if (sa !== sb) return sa - sb;
      return wa - wb;
    })
    .map((key) => {
      const w = windowStatsMap[key];
      const duration = w.window_duration_sec > 0 ? w.window_duration_sec : 1;
      const achievedQps = w.request_count / duration;
      const bizSuccessRate = w.request_count > 0 ? w.biz_success_count / w.request_count : 0;
      const acceptedRate = w.request_count > 0 ? w.accepted_count / w.request_count : 0;
      const reachedInject = w.window_target > 0 ? achievedQps / w.window_target : 0;
      const stable = reachedInject >= TURNING_INJECT_RATIO
        && bizSuccessRate >= TURNING_BIZ_SUCCESS_RATE
        && w.p95_ms <= TURNING_P95_MS;
      return {
        stage_index: w.stage_index,
        window_index: w.window_index,
        stage_target: w.stage_target,
        window_target: w.window_target,
        window_duration_sec: w.window_duration_sec,
        achieved_qps: achievedQps,
        injection_ratio: reachedInject,
        biz_success_rate: bizSuccessRate,
        accepted_rate: acceptedRate,
        p95_ms: w.p95_ms,
        stable: stable,
      };
    });

  let turningPoint = null;
  let extremeQps = 0;
  let extremeStageIndex = null;
  let extremeWindowIndex = null;
  windowMetrics.forEach((w) => {
    if (w.stable && w.achieved_qps > extremeQps) {
      extremeQps = w.achieved_qps;
      extremeStageIndex = w.stage_index;
      extremeWindowIndex = w.window_index;
    }
    if (!turningPoint && !w.stable && w.window_target > 0) {
      turningPoint = {
        scope: 'window',
        stage_index: w.stage_index,
        window_index: w.window_index,
        stage_target: w.stage_target,
        window_target: w.window_target,
        achieved_qps: w.achieved_qps,
        injection_ratio: w.injection_ratio,
        biz_success_rate: w.biz_success_rate,
        p95_ms: w.p95_ms,
      };
    }
  });

  stageMetrics.forEach((s) => {
    if (s.stable && s.achieved_qps > extremeQps) {
      extremeQps = s.achieved_qps;
      extremeStageIndex = s.stage_index;
      extremeWindowIndex = null;
    }
    if (!turningPoint && !s.stable && s.stage_target > 0) {
      turningPoint = {
        scope: 'stage',
        stage_index: s.stage_index,
        stage_target: s.stage_target,
        achieved_qps: s.achieved_qps,
        injection_ratio: s.injection_ratio,
        biz_success_rate: s.biz_success_rate,
        p95_ms: s.p95_ms,
      };
    }
  });

  const stableSegments = [];
  let openSegment = null;
  windowMetrics.forEach((w) => {
    if (w.stable) {
      if (!openSegment) {
        openSegment = {
          start_stage_index: w.stage_index,
          start_window_index: w.window_index,
          end_stage_index: w.stage_index,
          end_window_index: w.window_index,
          windows: 1,
          max_achieved_qps: w.achieved_qps,
        };
      } else {
        openSegment.end_stage_index = w.stage_index;
        openSegment.end_window_index = w.window_index;
        openSegment.windows += 1;
        if (w.achieved_qps > openSegment.max_achieved_qps) {
          openSegment.max_achieved_qps = w.achieved_qps;
        }
      }
    } else if (openSegment) {
      stableSegments.push(openSegment);
      openSegment = null;
    }
  });
  if (openSegment) {
    stableSegments.push(openSegment);
  }

  const limitDerivedFrom = extremeQps > 0
    ? (extremeWindowIndex == null ? 'stable_stages' : 'stable_windows')
    : 'global_biz_success_qps_fallback';
  if (extremeQps <= 0) {
    extremeQps = bizSuccessQps;
  }

  const localOptimistic = BASE_URL.indexOf('127.0.0.1') >= 0 || BASE_URL.indexOf('localhost') >= 0 || BASE_URL.indexOf('::1') >= 0;
  const optimismReasons = [];
  if (localOptimistic) {
    optimismReasons.push('load generator and target service share loopback network');
    optimismReasons.push('no real switch/router RTT or packet loss in path');
    optimismReasons.push('resource contention can hide real cross-host limits');
  }

  const extendedSummary = {
    analysis_version: 'v4',
    mode: {
      test_mode: TEST_MODE,
      target_mode: TARGET_MODE,
      base_url: BASE_URL,
    },
    qps: {
      iterations_rate: getRate(metrics.iterations),
      http_reqs_rate: totalReqRate,
      biz_success_qps: bizSuccessQps,
      biz_accepted_qps: bizAcceptedQps,
      estimated_limit_qps: extremeQps,
      extreme_qps: extremeQps,
    },
    totals: {
      total_requests: totalReqCount,
      biz_success_count: bizSuccessCount,
      biz_accepted_count: bizAcceptedCount,
      biz_fail_count: bizFailCount,
      degraded_count: degradedCount,
      status_503_count: status503Count,
      dropped_iterations: getCount(metrics.dropped_iterations),
    },
    turning_point: turningPoint,
    stage_metrics: stageMetrics,
    window_metrics: windowMetrics,
    stable_segments: stableSegments,
    turning_rule: {
      window_sec: WINDOW_SEC,
      min_injection_ratio: TURNING_INJECT_RATIO,
      min_biz_success_rate: TURNING_BIZ_SUCCESS_RATE,
      max_p95_ms: TURNING_P95_MS,
      extreme_qps_definition: EXTREME_QPS_DEFINITION,
      extreme_qps_derived_from: limitDerivedFrom,
      extreme_qps_stage_index: extremeStageIndex,
      extreme_qps_window_index: extremeWindowIndex,
    },
    local_run_assessment: {
      is_local_optimistic: localOptimistic,
      reasons: optimismReasons,
    },
  };

  return {
    'k6-summary.json': JSON.stringify(data, null, 2),
    'k6-summary-extended.json': JSON.stringify(extendedSummary, null, 2),
  };
}
