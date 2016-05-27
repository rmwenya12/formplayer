package services.impl;

import install.FormplayerConfigEngine;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import services.InstallService;

import java.io.IOException;

/**
 * Created by willpride on 2/25/16.
 */
public class InstallServiceImpl implements InstallService {


    private final String host;

    public InstallServiceImpl(String host){
        this.host = host;
    }

    @Override
    public FormplayerConfigEngine configureApplication(String reference, String username, String dbPath) throws IOException, InstallCancelledException, UnresolvedResourceException, UnfullfilledRequirementsException {
        FormplayerConfigEngine engine = new FormplayerConfigEngine(username, dbPath);
        reference = host + reference;
        if(reference.endsWith(".ccz")){
            engine.initFromArchive(reference);
        } else if(reference.endsWith(".ccpr")) {
            engine.initFromLocalFileResource(reference);
        } else {
            throw new RuntimeException("Can't instantiate with reference: " + reference);
        }
        engine.initEnvironment();
        return engine;
    }
}