package io.github.nickm980.smallville.api.v1.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ModelsResponse {
    private String providerMode;
    private String defaultModel;
    private List<String> availableModels = new ArrayList<String>();
    private Map<String, String> agentModelOverrides = new TreeMap<String, String>();

    public String getProviderMode() {
	return providerMode;
    }

    public void setProviderMode(String providerMode) {
	this.providerMode = providerMode;
    }

    public String getDefaultModel() {
	return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
	this.defaultModel = defaultModel;
    }

    public List<String> getAvailableModels() {
	return availableModels;
    }

    public void setAvailableModels(List<String> availableModels) {
	this.availableModels = availableModels;
    }

    public Map<String, String> getAgentModelOverrides() {
	return agentModelOverrides;
    }

    public void setAgentModelOverrides(Map<String, String> agentModelOverrides) {
	this.agentModelOverrides = agentModelOverrides;
    }
}
