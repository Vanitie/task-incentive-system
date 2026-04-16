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
const DATA_SCALE = __ENV.DATA_SCALE || 'original'; // original|qps_4000|qps_6000
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
const load_http_reqs = new Counter('load_http_reqs');
const load_req_failed = new Rate('load_req_failed');
const load_req_duration = new Trend('load_req_duration', true);
const endpoint_reqs = new Counter('endpoint_reqs');
const endpoint_biz_success_reqs = new Counter('endpoint_biz_success_reqs');
const endpoint_accepted_reqs = new Counter('endpoint_accepted_reqs');
const endpoint_fail_reqs = new Counter('endpoint_fail_reqs');
const endpoint_degraded_reqs = new Counter('endpoint_degraded_reqs');
const endpoint_req_duration = new Trend('endpoint_req_duration', true);

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

function roundNumber(value, digits = 2) {
  if (!Number.isFinite(value)) return 0;
  const factor = Math.pow(10, digits);
  return Math.round(value * factor) / factor;
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
const LOAD_DURATION_SEC = TEST_MODE === 'max'
  ? STAGES.reduce((sum, stage) => sum + Math.max(1, parseDurationToSec(stage.duration)), 0)
  : Math.max(1, parseDurationToSec(DURATION));

function buildScenarios() {
  let scenarios;
  if (TEST_MODE === 'compare') {
    const half = Math.max(1, Math.floor(RATE / 2));
    scenarios = {
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
  } else if (TEST_MODE === 'max') {
    scenarios = {
      max_probe: {
        executor: 'ramping-arrival-rate',
        startRate: STAGES[0] ? STAGES[0].start : START_RATE,
        timeUnit: '1s',
        preAllocatedVUs: PRE_VUS,
        maxVUs: MAX_VUS,
        stages: STAGES.map((s) => ({ target: s.target, duration: s.duration })),
      },
    };
  } else {
    scenarios = {
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

  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: {
    load_req_failed: ['rate<0.01'],
    load_req_duration: ['p(95)<500', 'p(99)<1200'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
};

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const BEARER_TOKEN = __ENV.BEARER_TOKEN || '';
const DUPLICATE_RATE = Number(__ENV.DUPLICATE_RATE || 0.1);
const NO_MSG_ID_RATE = Number(__ENV.NO_MSG_ID_RATE || 0.03);
const USER_ID_MIN = BigInt(__ENV.USER_ID_MIN || '2000000000000000000');
const USER_ID_MAX = BigInt(__ENV.USER_ID_MAX || '2000000000000396339');
const USER_ID_SPAN = USER_ID_MAX >= USER_ID_MIN ? (USER_ID_MAX - USER_ID_MIN + 1n) : 1n;
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

function getAvg(metric) {
  if (!metric || !metric.values || typeof metric.values.avg !== 'number') return 0;
  return metric.values.avg;
}

function getP90(metric) {
  if (!metric || !metric.values || typeof metric.values['p(90)'] !== 'number') return 0;
  return metric.values['p(90)'];
}

function getP95(metric) {
  if (!metric || !metric.values || typeof metric.values['p(95)'] !== 'number') return 0;
  return metric.values['p(95)'];
}

function getP99(metric) {
  if (!metric || !metric.values || typeof metric.values['p(99)'] !== 'number') return 0;
  return metric.values['p(99)'];
}

function getMax(metric) {
  if (!metric || !metric.values || typeof metric.values.max !== 'number') return 0;
  return metric.values.max;
}

function sanitizeModePart(raw, fallback) {
  const value = typeof raw === 'string' && raw.trim() ? raw.trim().toLowerCase() : fallback;
  return value.replace(/[^a-z0-9_-]+/g, '_');
}

function buildAuthHeaders(extraHeaders = {}) {
  const headers = { ...extraHeaders };
  if (BEARER_TOKEN) {
    headers.Authorization = `Bearer ${BEARER_TOKEN}`;
  }
  return headers;
}

function endpointLabel(endpointMetric) {
  if (endpointMetric && endpointMetric.scenario_name && endpointMetric.scenario_name.indexOf('compare_') === 0) {
    return endpointMetric.scenario_name.replace('compare_', '').toUpperCase();
  }
  return String(endpointMetric && endpointMetric.endpoint_type ? endpointMetric.endpoint_type : 'unknown').toUpperCase();
}

function pickUserId(vu, iter) {
  // Use contiguous range [USER_ID_MIN, USER_ID_MAX], deterministic by VU/ITER.
  const cursor = BigInt((vu - 1) * 1000000 + iter);
  const offset = USER_ID_SPAN > 0n ? (cursor % USER_ID_SPAN) : 0n;
  return (USER_ID_MIN + offset).toString();
}

function buildRandomRequestId(vu, iter) {
  // Keep a readable prefix for log tracing while adding enough entropy per request.
  const now = Date.now().toString(36);
  const r1 = Math.floor(Math.random() * 0x100000000).toString(36);
  const r2 = Math.floor(Math.random() * 0x100000000).toString(36);
  return `req-${RUN_ID}-${vu}-${iter}-${now}-${r1}${r2}`;
}

export default function () {
  const userId = pickUserId(__VU, __ITER);
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
  const requestId = buildRandomRequestId(__VU, __ITER);
  const messageId = useDuplicate ? pick(DUP_POOL) : uniqueId;

  const payload = {
    userId: userId,
    eventType: pick(EVENT_TYPES),
    value: Math.floor(Math.random() * 5) + 1,
    time: new Date().toISOString(),
    requestId: requestId,
    eventId: `evt-${uniqueId}`,
    deviceId: `device-${((__VU + __ITER) % 500) + 1}`,
    ip: `10.10.${(__VU + __ITER) % 20}.${((__VU + __ITER) % 200) + 1}`,
    channel: 'k6',
  };
  if (!dropMessageId) {
    payload.messageId = messageId;
  }

  const headers = buildAuthHeaders({ 'Content-Type': 'application/json' });

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
  const endpointTags = {
    endpoint_type: endpointType,
    scenario_name: exec.scenario.name || 'na',
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

  load_http_reqs.add(1);
  load_req_failed.add(!accepted);
  load_req_duration.add(res.timings.duration);

  biz_success_reqs.add(bizSuccess ? 1 : 0, tags);
  biz_accepted_reqs.add(accepted ? 1 : 0, tags);
  biz_fail_reqs.add(accepted ? 0 : 1, tags);
  biz_degraded_reqs.add(degraded ? 1 : 0, tags);
  biz_success_rate.add(bizSuccess, tags);
  biz_accepted_rate.add(accepted, tags);
  endpoint_reqs.add(1, endpointTags);
  endpoint_biz_success_reqs.add(bizSuccess ? 1 : 0, endpointTags);
  endpoint_accepted_reqs.add(accepted ? 1 : 0, endpointTags);
  endpoint_fail_reqs.add(accepted ? 0 : 1, endpointTags);
  endpoint_degraded_reqs.add(degraded ? 1 : 0, endpointTags);
  endpoint_req_duration.add(res.timings.duration, endpointTags);
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
  const effectiveLoadDurationSec = LOAD_DURATION_SEC > 0 ? LOAD_DURATION_SEC : totalDurationSec;
  const totalReqCount = getCount(metrics.load_http_reqs);
  const totalReqRate = effectiveLoadDurationSec > 0 ? totalReqCount / effectiveLoadDurationSec : 0;
  const bizSuccessCount = getCount(metrics.biz_success_reqs);
  const bizAcceptedCount = getCount(metrics.biz_accepted_reqs);
  const bizFailCount = getCount(metrics.biz_fail_reqs);
  const degradedCount = getCount(metrics.biz_degraded_reqs);
  const status503Count = getCount(metrics.status_503_reqs);

  const bizSuccessQps = effectiveLoadDurationSec > 0 ? bizSuccessCount / effectiveLoadDurationSec : 0;
  const bizAcceptedQps = effectiveLoadDurationSec > 0 ? bizAcceptedCount / effectiveLoadDurationSec : 0;
  const loadLatency = {
    avg_ms: getAvg(metrics.load_req_duration),
    p90_ms: getP90(metrics.load_req_duration),
    p95_ms: getP95(metrics.load_req_duration),
    p99_ms: getP99(metrics.load_req_duration),
    max_ms: getMax(metrics.load_req_duration),
  };

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
          avg_ms: 0,
          p90_ms: 0,
          p95_ms: 0,
          p99_ms: 0,
        };
      }
      const slot = stageStatsMap[stageIndex];
      if (metricName === 'stage_reqs') slot.request_count = getCount(metrics[name]);
      if (metricName === 'stage_biz_success_reqs') slot.biz_success_count = getCount(metrics[name]);
      if (metricName === 'stage_accepted_reqs') slot.accepted_count = getCount(metrics[name]);
      if (metricName === 'stage_fail_reqs') slot.fail_count = getCount(metrics[name]);
      if (metricName === 'stage_req_duration') {
        slot.avg_ms = getAvg(metrics[name]);
        slot.p90_ms = getP90(metrics[name]);
        slot.p95_ms = getP95(metrics[name]);
        slot.p99_ms = getP99(metrics[name]);
      }
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
        avg_ms: 0,
        p90_ms: 0,
        p95_ms: 0,
        p99_ms: 0,
      };
    }
    const slot = windowStatsMap[key];
    if (metricName === 'window_reqs') slot.request_count = getCount(metrics[name]);
    if (metricName === 'window_biz_success_reqs') slot.biz_success_count = getCount(metrics[name]);
    if (metricName === 'window_accepted_reqs') slot.accepted_count = getCount(metrics[name]);
    if (metricName === 'window_fail_reqs') slot.fail_count = getCount(metrics[name]);
    if (metricName === 'window_req_duration') {
      slot.avg_ms = getAvg(metrics[name]);
      slot.p90_ms = getP90(metrics[name]);
      slot.p95_ms = getP95(metrics[name]);
      slot.p99_ms = getP99(metrics[name]);
    }
  });

  const endpointStatsMap = {};
  Object.keys(metrics).forEach((name) => {
    const endpointMatch = /^(endpoint_reqs|endpoint_biz_success_reqs|endpoint_accepted_reqs|endpoint_fail_reqs|endpoint_degraded_reqs|endpoint_req_duration)\{(.+)}$/.exec(name);
    if (!endpointMatch) return;

    const metricName = endpointMatch[1];
    const tags = parseTags(endpointMatch[2]);
    const endpointType = tags.endpoint_type || 'unknown';
    const scenarioName = tags.scenario_name || 'na';
    const key = `${scenarioName}|${endpointType}`;
    if (!endpointStatsMap[key]) {
      endpointStatsMap[key] = {
        endpoint_type: endpointType,
        scenario_name: scenarioName,
        request_count: 0,
        biz_success_count: 0,
        accepted_count: 0,
        fail_count: 0,
        degraded_count: 0,
        avg_ms: 0,
        p90_ms: 0,
        p95_ms: 0,
        p99_ms: 0,
      };
    }

    const slot = endpointStatsMap[key];
    if (metricName === 'endpoint_reqs') slot.request_count = getCount(metrics[name]);
    if (metricName === 'endpoint_biz_success_reqs') slot.biz_success_count = getCount(metrics[name]);
    if (metricName === 'endpoint_accepted_reqs') slot.accepted_count = getCount(metrics[name]);
    if (metricName === 'endpoint_fail_reqs') slot.fail_count = getCount(metrics[name]);
    if (metricName === 'endpoint_degraded_reqs') slot.degraded_count = getCount(metrics[name]);
    if (metricName === 'endpoint_req_duration') {
      slot.avg_ms = getAvg(metrics[name]);
      slot.p90_ms = getP90(metrics[name]);
      slot.p95_ms = getP95(metrics[name]);
      slot.p99_ms = getP99(metrics[name]);
    }
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
        avg_ms: s.avg_ms,
        p90_ms: s.p90_ms,
        p95_ms: s.p95_ms,
        p99_ms: s.p99_ms,
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
        avg_ms: w.avg_ms,
        p90_ms: w.p90_ms,
        p95_ms: w.p95_ms,
        p99_ms: w.p99_ms,
        stable: stable,
      };
    });

  const endpointMetrics = Object.keys(endpointStatsMap)
    .sort((a, b) => {
      const left = endpointStatsMap[a];
      const right = endpointStatsMap[b];
      const order = { async: 0, sync: 1, direct: 2, noop: 3, unknown: 9 };
      const leftOrder = Object.prototype.hasOwnProperty.call(order, left.endpoint_type) ? order[left.endpoint_type] : 9;
      const rightOrder = Object.prototype.hasOwnProperty.call(order, right.endpoint_type) ? order[right.endpoint_type] : 9;
      const endpointOrder = leftOrder - rightOrder;
      if (endpointOrder !== 0) return endpointOrder;
      return String(left.scenario_name).localeCompare(String(right.scenario_name));
    })
    .map((key) => {
      const endpoint = endpointStatsMap[key];
      const requestCount = endpoint.request_count;
      return {
        endpoint_label: endpointLabel(endpoint),
        endpoint_type: endpoint.endpoint_type,
        scenario_name: endpoint.scenario_name,
        request_count: requestCount,
        biz_success_count: endpoint.biz_success_count,
        accepted_count: endpoint.accepted_count,
        fail_count: endpoint.fail_count,
        degraded_count: endpoint.degraded_count,
        achieved_qps: effectiveLoadDurationSec > 0 ? requestCount / effectiveLoadDurationSec : 0,
        biz_success_qps: effectiveLoadDurationSec > 0 ? endpoint.biz_success_count / effectiveLoadDurationSec : 0,
        accepted_qps: effectiveLoadDurationSec > 0 ? endpoint.accepted_count / effectiveLoadDurationSec : 0,
        biz_success_rate: requestCount > 0 ? endpoint.biz_success_count / requestCount : 0,
        accepted_rate: requestCount > 0 ? endpoint.accepted_count / requestCount : 0,
        degraded_rate: requestCount > 0 ? endpoint.degraded_count / requestCount : 0,
        avg_ms: endpoint.avg_ms,
        p90_ms: endpoint.p90_ms,
        p95_ms: endpoint.p95_ms,
        p99_ms: endpoint.p99_ms,
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

  const chartData = {
    endpoint_qps_bar: {
      chart_type: 'bar',
      available: endpointMetrics.length > 0,
      title: '接口吞吐对比',
      x_axis: endpointMetrics.map((item) => item.endpoint_label),
      series: [
        {
          name: 'actual_qps',
          unit: 'req/s',
          data: endpointMetrics.map((item) => roundNumber(item.achieved_qps)),
        },
        {
          name: 'biz_success_qps',
          unit: 'req/s',
          data: endpointMetrics.map((item) => roundNumber(item.biz_success_qps)),
        },
      ],
    },
    endpoint_latency_bar: {
      chart_type: 'bar',
      available: endpointMetrics.length > 0,
      title: '接口延迟分位对比',
      x_axis: endpointMetrics.map((item) => item.endpoint_label),
      series: [
        {
          name: 'p90_ms',
          unit: 'ms',
          data: endpointMetrics.map((item) => roundNumber(item.p90_ms)),
        },
        {
          name: 'p95_ms',
          unit: 'ms',
          data: endpointMetrics.map((item) => roundNumber(item.p95_ms)),
        },
        {
          name: 'p99_ms',
          unit: 'ms',
          data: endpointMetrics.map((item) => roundNumber(item.p99_ms)),
        },
      ],
    },
    stage_qps_latency_line: {
      chart_type: 'line',
      available: stageMetrics.length > 0,
      title: '阶段吞吐与尾延迟变化',
      x_axis: stageMetrics.map((item) => `S${item.stage_index}`),
      series: [
        {
          name: 'achieved_qps',
          unit: 'req/s',
          data: stageMetrics.map((item) => roundNumber(item.achieved_qps)),
        },
        {
          name: 'p95_ms',
          unit: 'ms',
          data: stageMetrics.map((item) => roundNumber(item.p95_ms)),
        },
        {
          name: 'p99_ms',
          unit: 'ms',
          data: stageMetrics.map((item) => roundNumber(item.p99_ms)),
        },
      ],
    },
    window_qps_latency_line: {
      chart_type: 'line',
      available: windowMetrics.length > 0,
      title: '窗口吞吐与延迟变化',
      x_axis: windowMetrics.map((item) => `S${item.stage_index}-W${item.window_index}`),
      series: [
        {
          name: 'achieved_qps',
          unit: 'req/s',
          data: windowMetrics.map((item) => roundNumber(item.achieved_qps)),
        },
        {
          name: 'p95_ms',
          unit: 'ms',
          data: windowMetrics.map((item) => roundNumber(item.p95_ms)),
        },
        {
          name: 'p99_ms',
          unit: 'ms',
          data: windowMetrics.map((item) => roundNumber(item.p99_ms)),
        },
        {
          name: 'biz_success_rate',
          unit: 'ratio',
          data: windowMetrics.map((item) => roundNumber(item.biz_success_rate, 4)),
        },
      ],
    },
  };

  const extendedSummary = {
    analysis_version: 'v6',
    mode: {
      test_mode: TEST_MODE,
      target_mode: TARGET_MODE,
      base_url: BASE_URL,
    },
    resource_monitoring: {
      recommended_strategy: 'external_os_sampler',
      reason: 'monitor endpoints can be rejected under load and monitor-side latency percentiles are not trusted',
    },
    qps: {
      iterations_rate: effectiveLoadDurationSec > 0 ? getCount(metrics.iterations) / effectiveLoadDurationSec : getRate(metrics.iterations),
      http_reqs_rate: totalReqRate,
      biz_success_qps: bizSuccessQps,
      biz_accepted_qps: bizAcceptedQps,
      estimated_limit_qps: extremeQps,
      extreme_qps: extremeQps,
    },
    latency: loadLatency,
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
    endpoint_metrics: endpointMetrics,
    stage_metrics: stageMetrics,
    window_metrics: windowMetrics,
    charts: chartData,
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

  const summaryMode = sanitizeModePart(TEST_MODE, 'baseline');
  const summaryTarget = sanitizeModePart(TARGET_MODE, 'async');
  const summaryScale = sanitizeModePart(DATA_SCALE, 'original');
  const summaryFile = `k6-summary-${summaryScale}-${summaryMode}-${summaryTarget}.json`;
  const extendedSummaryFile = `k6-summary-extended-${summaryScale}-${summaryMode}-${summaryTarget}.json`;

  return {
    [summaryFile]: JSON.stringify(data, null, 2),
    [extendedSummaryFile]: JSON.stringify(extendedSummary, null, 2),
  };
}
