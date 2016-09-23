package services;

import auth.HqAuth;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Factory that determines the correct URL endpoint based on domain, host, and username/asUsername,
 * then retrieves and returns the restore XML.
 */
public class RestoreFactory {
    @Value("${commcarehq.host}")
    private String host;

    private String asUsername;
    private String username;
    private String domain;
    private HqAuth hqAuth;

    private final Log log = LogFactory.getLog(RestoreFactory.class);


    public String getRestoreXml() {
        if (domain == null || (username == null && asUsername == null)) {
            throw new RuntimeException("Domain and one of username or asUsername must be non-null. " +
                    " Domain: " + domain +
                    ", username: " + username +
                    ", asUsername: " + asUsername);
        }
        String restoreUrl;
        if (asUsername == null) {
            restoreUrl = getRestoreUrl(host, domain);
        } else {
            restoreUrl = getRestoreUrl(host, domain, asUsername);
        }

        log.info("Restoring from URL " + restoreUrl);
        return getRestoreXmlHelper(restoreUrl, hqAuth);
    }

    private static String getRestoreXmlHelper(String restoreUrl, HqAuth auth) {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response =
                restTemplate.exchange(restoreUrl,
                        HttpMethod.GET,
                        new HttpEntity<String>(auth.getAuthHeaders()), String.class);
        return response.getBody();
    }

    public static String getRestoreUrl(String host, String domain){
        return host + "/a/" + domain + "/phone/restore/?version=2.0";
    }

    public String getRestoreUrl(String host, String domain, String username) {
        return host + "/hq/admin/phone/restore/?as=" + username + "@" +
                domain + ".commcarehq.org&version=2.0&raw=true";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public HqAuth getHqAuth() {
        return hqAuth;
    }

    public void setHqAuth(HqAuth hqAuth) {
        this.hqAuth = hqAuth;
    }

    public String getAsUsername() {
        return asUsername;
    }

    public void setAsUsername(String asUsername) {
        this.asUsername = asUsername;
    }
}
