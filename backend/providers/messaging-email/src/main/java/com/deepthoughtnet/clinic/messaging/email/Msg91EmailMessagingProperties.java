package com.deepthoughtnet.clinic.messaging.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized MSG91 SMTP settings. Secrets are supplied by environment/configuration. */
@ConfigurationProperties(prefix = "clinic.carepilot.messaging.email.providers.msg91")
public class Msg91EmailMessagingProperties {
    private boolean enabled;
    private String host = "smtp.mailer91.com";
    private int port = 587;
    private String username;
    private String password;
    private String from;
    private boolean auth = true;
    private boolean starttls = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public boolean isAuth() { return auth; }
    public void setAuth(boolean auth) { this.auth = auth; }
    public boolean isStarttls() { return starttls; }
    public void setStarttls(boolean starttls) { this.starttls = starttls; }
}
