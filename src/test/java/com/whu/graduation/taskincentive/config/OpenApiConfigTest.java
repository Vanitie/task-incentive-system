package com.whu.graduation.taskincentive.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiConfigTest {

    @Test
    void taskIncentiveOpenAPI_shouldBuildBearerScheme() {
        OpenApiConfig config = new OpenApiConfig();

        OpenAPI api = config.taskIncentiveOpenAPI();

        assertEquals("任务激励系统 API", api.getInfo().getTitle());
        assertNotNull(api.getComponents().getSecuritySchemes().get("bearerAuth"));
        assertEquals(SecurityScheme.Type.HTTP,
                api.getComponents().getSecuritySchemes().get("bearerAuth").getType());
        assertEquals("bearer", api.getComponents().getSecuritySchemes().get("bearerAuth").getScheme());
        assertEquals("bearerAuth", api.getSecurity().get(0).keySet().iterator().next());
    }
}

