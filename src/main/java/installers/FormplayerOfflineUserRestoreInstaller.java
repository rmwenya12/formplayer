package installers;

import org.commcare.resources.model.installers.OfflineUserRestoreInstaller;
import org.commcare.suite.model.OfflineUserRestore;
import org.javarosa.core.services.storage.IStorageUtility;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import services.FormplayerStorageFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by willpride on 12/1/16.
 */
public class FormplayerOfflineUserRestoreInstaller extends OfflineUserRestoreInstaller {

    FormplayerStorageFactory storageFactory;

    public FormplayerOfflineUserRestoreInstaller(){}

    public FormplayerOfflineUserRestoreInstaller(FormplayerStorageFactory storageFactory) {
        this.storageFactory = storageFactory;
    }

    @Override
    protected IStorageUtility<OfflineUserRestore> storage() {
        if (cacheStorage == null) {
            cacheStorage = storageFactory.newStorage(OfflineUserRestore.STORAGE_KEY, OfflineUserRestore.class);
        }
        return cacheStorage;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);
        String databasePath = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        String trimmedUsername = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        storageFactory = new FormplayerStorageFactory();
        storageFactory.configure(databasePath, trimmedUsername);

    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(storageFactory.getDatabasePath()));
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(storageFactory.getTrimmedUsername()));
    }
}