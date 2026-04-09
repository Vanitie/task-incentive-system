package com.whu.graduation.taskincentive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Security security = new Security();
    private Dlq dlq = new Dlq();
    private Dedup dedup = new Dedup();
    private CacheWarmup cacheWarmup = new CacheWarmup();

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Dlq getDlq() {
        return dlq;
    }

    public void setDlq(Dlq dlq) {
        this.dlq = dlq;
    }

    public Dedup getDedup() {
        return dedup;
    }

    public void setDedup(Dedup dedup) {
        this.dedup = dedup;
    }

    public CacheWarmup getCacheWarmup() {
        return cacheWarmup;
    }

    public void setCacheWarmup(CacheWarmup cacheWarmup) {
        this.cacheWarmup = cacheWarmup;
    }

    public static class Security {
        private Admin admin = new Admin();
        private Jwt jwt = new Jwt();

        public Admin getAdmin() { return admin; }
        public void setAdmin(Admin admin) { this.admin = admin; }
        public Jwt getJwt() { return jwt; }
        public void setJwt(Jwt jwt) { this.jwt = jwt; }

        public static class Admin {
            private String username;
            private String password;
            public String getUsername() { return username; }
            public void setUsername(String username) { this.username = username; }
            public String getPassword() { return password; }
            public void setPassword(String password) { this.password = password; }
        }

        public static class Jwt {
            private String secret;
            private long expirationMs = 86400000L; // 24h default
            public String getSecret() { return secret; }
            public void setSecret(String secret) { this.secret = secret; }
            public long getExpirationMs() { return expirationMs; }
            public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
        }
    }

    public static class Dlq {
        private String topic = "dlq-topic";
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }

    public static class Dedup {
        private long ttlDays = 7L;
        public long getTtlDays() { return ttlDays; }
        public void setTtlDays(long ttlDays) { this.ttlDays = ttlDays; }
    }

    public static class CacheWarmup {
        private boolean enabled = true;
        private boolean failFast = false;
        private boolean loadRisk = true;
        private boolean loadTaskConfig = true;
        private boolean loadHotUsers = true;
        private int taskConfigBatchSize = 500;
        private long taskConfigRedisTtlSeconds = 60L;
        private int hotUserLimit = 1000;
        private int instancesPerHotUser = 30;
        private int maxTotalHotUserInstances = 30000;
        private long userTaskRedisTtlMinutes = 10L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isFailFast() { return failFast; }
        public void setFailFast(boolean failFast) { this.failFast = failFast; }

        public boolean isLoadRisk() { return loadRisk; }
        public void setLoadRisk(boolean loadRisk) { this.loadRisk = loadRisk; }

        public boolean isLoadTaskConfig() { return loadTaskConfig; }
        public void setLoadTaskConfig(boolean loadTaskConfig) { this.loadTaskConfig = loadTaskConfig; }

        public boolean isLoadHotUsers() { return loadHotUsers; }
        public void setLoadHotUsers(boolean loadHotUsers) { this.loadHotUsers = loadHotUsers; }

        public int getTaskConfigBatchSize() { return taskConfigBatchSize; }
        public void setTaskConfigBatchSize(int taskConfigBatchSize) { this.taskConfigBatchSize = taskConfigBatchSize; }

        public long getTaskConfigRedisTtlSeconds() { return taskConfigRedisTtlSeconds; }
        public void setTaskConfigRedisTtlSeconds(long taskConfigRedisTtlSeconds) { this.taskConfigRedisTtlSeconds = taskConfigRedisTtlSeconds; }

        public int getHotUserLimit() { return hotUserLimit; }
        public void setHotUserLimit(int hotUserLimit) { this.hotUserLimit = hotUserLimit; }

        public int getInstancesPerHotUser() { return instancesPerHotUser; }
        public void setInstancesPerHotUser(int instancesPerHotUser) { this.instancesPerHotUser = instancesPerHotUser; }

        public int getMaxTotalHotUserInstances() { return maxTotalHotUserInstances; }
        public void setMaxTotalHotUserInstances(int maxTotalHotUserInstances) { this.maxTotalHotUserInstances = maxTotalHotUserInstances; }

        public long getUserTaskRedisTtlMinutes() { return userTaskRedisTtlMinutes; }
        public void setUserTaskRedisTtlMinutes(long userTaskRedisTtlMinutes) { this.userTaskRedisTtlMinutes = userTaskRedisTtlMinutes; }
    }
}
