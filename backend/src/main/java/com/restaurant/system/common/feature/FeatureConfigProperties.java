package com.restaurant.system.common.feature;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.features")
public class FeatureConfigProperties {

    private boolean corePos = true;
    private boolean printing = true;
    private boolean kds = false;
    private boolean admin = true;
    private boolean analytics = true;
    private boolean platform = false;
    private boolean developerTools = false;

    public boolean isCorePos() {
        return corePos;
    }

    public void setCorePos(boolean corePos) {
        this.corePos = corePos;
    }

    public boolean isPrinting() {
        return printing;
    }

    public void setPrinting(boolean printing) {
        this.printing = printing;
    }

    public boolean isKds() {
        return kds;
    }

    public void setKds(boolean kds) {
        this.kds = kds;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isAnalytics() {
        return analytics;
    }

    public void setAnalytics(boolean analytics) {
        this.analytics = analytics;
    }

    public boolean isPlatform() {
        return platform;
    }

    public void setPlatform(boolean platform) {
        this.platform = platform;
    }

    public boolean isDeveloperTools() {
        return developerTools;
    }

    public void setDeveloperTools(boolean developerTools) {
        this.developerTools = developerTools;
    }
}
