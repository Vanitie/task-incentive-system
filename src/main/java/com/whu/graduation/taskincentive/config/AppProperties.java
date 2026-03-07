package com.whu.graduation.taskincentive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Security security = new Security();
    private Dlq dlq = new Dlq();
    private Dedup dedup = new Dedup();

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
}
