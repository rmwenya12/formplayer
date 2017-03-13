package services;

import beans.InstallRequestBean;
import org.apache.commons.lang3.StringUtils;
import org.sqlite.SQLiteConnection;
import org.sqlite.javax.SQLiteConnectionPoolDataSource;
import sandbox.SqliteIndexedStorageUtility;
import sandbox.UserSqlSandbox;
import org.javarosa.core.services.storage.IStorageIndexedFactory;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.springframework.stereotype.Component;
import util.ApplicationUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * FormPlayer's storage factory that negotiates between parsers/installers and the storage layer
 */
@Component
public class FormplayerStorageFactory implements IStorageIndexedFactory, ConnectionHandler{

    private String username;
    private String domain;
    private String appId;
    private String databasePath;
    private String trimmedUsername;

    private static Connection connection;

    public void configure(InstallRequestBean authenticatedRequestBean) {
        configure(authenticatedRequestBean.getUsername(),
                authenticatedRequestBean.getDomain(),
                authenticatedRequestBean.getAppId());
    }

    public void configure(String username, String domain, String appId) {
        if(username == null || domain == null || appId == null) {
            throw new RuntimeException(String.format("Cannot configure FormplayerStorageFactory with null arguments. " +
                    "username = %s, domain = %s, appId = %s", username, domain, appId));
        }
        this.username = username;
        this.domain = domain;
        this.appId = appId;
        this.trimmedUsername = StringUtils.substringBefore(username, "@");
        this.databasePath = ApplicationUtils.getApplicationDBPath(domain, username, appId);
    }

    @Override
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                DataSource dataSource = UserSqlSandbox.getDataSource(trimmedUsername, databasePath);
                connection = dataSource.getConnection();
            } else if (!((SQLiteConnection) connection).url().contains(username) ||
                    !((SQLiteConnection) connection).url().contains(domain) ||
                    !((SQLiteConnection) connection).url().contains(appId)) {
                closeConnection();
                DataSource dataSource = UserSqlSandbox.getDataSource(trimmedUsername, databasePath);
                connection = dataSource.getConnection();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return connection;
    }

    @Override
    public IStorageUtilityIndexed newStorage(String name, Class type) {
        return new SqliteIndexedStorageUtility(this, type, databasePath, trimmedUsername, name);
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

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public String getTrimmedUsername() {
        return trimmedUsername;
    }

    public static void closeConnection() {
        try {
            if(connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
