package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.config.GeneralConfig;
import io.github.nickm980.smallville.runtime.RuntimeSettingsService;

public class RuntimeSettingsServiceTest {

    @Test
    public void test_resolve_model_prefers_agent_override_then_agent_model_then_default() {
	GeneralConfig config = new GeneralConfig();
	config.setApiPath("http://localhost:11434/v1/chat/completions");
	config.setModel("llama3.2:3b");

	RuntimeSettingsService runtime = new RuntimeSettingsService(config);
	runtime.setDefaultModel("qwen2.5:0.5b");
	runtime.setAgentModelOverride("Alex", "llama3.2:3b-16k");

	assertEquals("llama3.2:3b-16k", runtime.resolveModel("Alex", "mistral:latest"));
	assertEquals("mistral:latest", runtime.resolveModel("Jamie", "mistral:latest"));
	assertEquals("qwen2.5:0.5b", runtime.resolveModel("Jamie", null));
	assertTrue(runtime.isLocalProvider());
    }

    @Test
    public void test_ask_shadow_bridge_runtime_settings_preserve_safe_defaults_and_effective_normalization() {
	GeneralConfig config = new GeneralConfig();
	config.setApiPath("http://localhost:11434/v1/chat/completions");
	config.setModel("llama3.2:3b");

	RuntimeSettingsService runtime = new RuntimeSettingsService(config);

	assertFalse(runtime.isAskShadowBridgeEnabled());
	assertEquals("http://127.0.0.1:8010/neural/turn", runtime.getAskShadowBridgeEndpoint());
	assertEquals(200, runtime.getAskShadowBridgeConnectTimeoutMs());
	assertEquals(1200, runtime.getAskShadowBridgeCallTimeoutMs());

	config.setAskShadowBridgeEnabled(true);
	config.setAskShadowBridgeEndpoint("  http://127.0.0.1:9000/neural/turn  ");
	config.setAskShadowBridgeConnectTimeoutMs(40000);
	config.setAskShadowBridgeCallTimeoutMs(-5);

	assertTrue(runtime.isAskShadowBridgeEnabled());
	assertEquals("http://127.0.0.1:9000/neural/turn", runtime.getAskShadowBridgeEndpoint());
	assertEquals(30000, runtime.getAskShadowBridgeConnectTimeoutMs());
	assertEquals(1200, runtime.getAskShadowBridgeCallTimeoutMs());
    }
}
