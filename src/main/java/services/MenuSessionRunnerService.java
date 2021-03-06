package services;

import beans.NewFormResponse;
import beans.NotificationMessage;
import beans.menus.*;
import exceptions.ApplicationConfigException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commcare.modern.session.SessionWrapper;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.suite.model.Text;
import org.commcare.util.screen.*;
import org.javarosa.core.model.actions.FormSendCalloutHandler;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import repo.FormSessionRepo;
import repo.MenuSessionRepo;
import repo.SerializableMenuSession;
import screens.FormplayerQueryScreen;
import screens.FormplayerSyncScreen;
import session.FormSession;
import session.MenuSession;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Class containing logic for accepting a NewSessionRequest and services,
 * restoring the user, opening the new form, and returning the question list response.
 */
@Component
public class MenuSessionRunnerService {

    @Autowired
    private RestoreFactory restoreFactory;

    @Autowired
    private InstallService installService;

    @Value("${commcarehq.host}")
    private String host;

    @Value("${commcarehq.environment}")
    private String hqEnvironment;

    @Autowired
    private QueryRequester queryRequester;

    @Autowired
    private SyncRequester syncRequester;

    @Autowired
    protected FormSessionRepo formSessionRepo;

    @Autowired
    protected MenuSessionRepo menuSessionRepo;

    @Autowired
    protected MenuSessionFactory menuSessionFactory;

    @Autowired
    protected FormSendCalloutHandler formSendCalloutHandler;

    @Autowired
    protected FormplayerStorageFactory storageFactory;

    private static final Log log = LogFactory.getLog(MenuSessionRunnerService.class);


    public BaseResponseBean getNextMenu(MenuSession menuSession) throws Exception {
        return getNextMenu(menuSession, null, 0, "", 0);
    }

    private BaseResponseBean getNextMenu(MenuSession menuSession,
                                           String detailSelection,
                                           int offset,
                                           String searchText,
                                           int sortIndex) throws Exception {
        Screen nextScreen = menuSession.getNextScreen();
        // No next menu screen? Start form entry!
        if (nextScreen == null) {
            String assertionFailure = getAssertionFailure(menuSession);
            if (assertionFailure != null) {
                BaseResponseBean responseBean = new BaseResponseBean("App Configuration Error", assertionFailure, true, true);
                responseBean.setNotification(new NotificationMessage(assertionFailure, true));
                return responseBean;
            }
            return startFormEntry(menuSession);
        }

        MenuBean menuResponseBean;
        // We're looking at a module or form menu
        if (nextScreen instanceof MenuScreen) {
            menuResponseBean = new CommandListResponseBean((MenuScreen) nextScreen,
                    menuSession.getSessionWrapper(),
                    menuSession.getId()
            );
        } else if (nextScreen instanceof EntityScreen) {
            // We're looking at a case list or detail screen
            menuResponseBean = new EntityListResponse(
                    (EntityScreen)nextScreen,
                    menuSession.getEvalContextWithHereFuncHandler(),
                    detailSelection,
                    offset,
                    searchText,
                    sortIndex
            );
        } else if (nextScreen instanceof FormplayerQueryScreen){
            menuResponseBean = new QueryResponseBean(
                    (QueryScreen) nextScreen,
                    menuSession.getSessionWrapper()
            );
        } else {
            throw new Exception("Unable to recognize next screen: " + nextScreen);
        }

        menuResponseBean.setBreadcrumbs(menuSession.getBreadcrumbs());
        menuResponseBean.setAppId(menuSession.getAppId());
        menuResponseBean.setAppVersion(menuSession.getCommCareVersionString() +
                ", App Version: " + menuSession.getAppVersion());
        menuResponseBean.setPersistentCaseTile(getPersistentDetail(menuSession));
        return menuResponseBean;
    }

    public BaseResponseBean advanceSessionWithSelections(MenuSession menuSession,
                                                         String[] selections) throws Exception {
        return advanceSessionWithSelections(menuSession, selections, null, null, 0, null, 0);
    }

    /**
     * Advances the session based on the selections.
     *
     * @param menuSession
     * @param selections      - Selections are either an integer index into a list of modules
     *                        or a case id indicating the case selected for a case detail.
     *                        <p>
     *                        An example selection would be ["0", "2", "6c5d91e9-61a2-4264-97f3-5d68636ff316"]
     *                        <p>
     *                        This would mean select the 0th menu, then the 2nd menu, then the case with the id 6c5d91e9-61a2-4264-97f3-5d68636ff316.
     * @param detailSelection - If requesting a case detail will be a case id, else null. When the case id is given
     *                        it is used to short circuit the normal TreeReference calculation by inserting a predicate that
     *                        is [@case_id = <detailSelection>].
     * @param queryDictionary
     * @param offset
     * @param searchText
     */
    public BaseResponseBean advanceSessionWithSelections(MenuSession menuSession,
                                                         String[] selections,
                                                         String detailSelection,
                                                         Hashtable<String, String> queryDictionary,
                                                         int offset,
                                                         String searchText,
                                                         int sortIndex) throws Exception {
        BaseResponseBean nextResponse;
        // If we have no selections, we're are the root screen.
        if (selections == null) {
            return getNextMenu(
                    menuSession,
                    null,
                    offset,
                    searchText,
                    sortIndex
            );
        }
        NotificationMessage notificationMessage = new NotificationMessage();
        for (int i = 1; i <= selections.length; i++) {
            String selection = selections[i - 1];
            boolean gotNextScreen = menuSession.handleInput(selection);
            if (!gotNextScreen) {
                notificationMessage = new NotificationMessage(
                        "Overflowed selections with selection " + selection + " at index " + i, (true));
                break;
            }
            Screen nextScreen = menuSession.getNextScreen();

            if (nextScreen instanceof FormplayerQueryScreen && queryDictionary != null) {
                notificationMessage = doQuery(
                        (FormplayerQueryScreen) nextScreen,
                        menuSession,
                        queryDictionary
                );
            }
            if (nextScreen instanceof FormplayerSyncScreen) {
                BaseResponseBean syncResponse = doSyncGetNext(
                        (FormplayerSyncScreen) nextScreen,
                        menuSession);
                if (syncResponse != null) {
                    return syncResponse;
                }
            }
        }
        nextResponse = getNextMenu(
                menuSession,
                detailSelection,
                offset,
                searchText,
                sortIndex
        );
        if (nextResponse != null) {
            if (nextResponse.getNotification() == null) {
                nextResponse.setNotification(notificationMessage);
            }
            log.info("Returning menu: " + nextResponse);
            return nextResponse;
        } else {
            BaseResponseBean responseBean = resolveFormGetNext(menuSession);
            if (responseBean == null) {
                responseBean = new BaseResponseBean(null, null, false, true);
            }
            return responseBean;
        }
    }


    /**
     * Perform the sync and update the notification and screen accordingly.
     * After a sync, we can either pop another menu/form to begin
     * or just return to the app menu.
     */
    private BaseResponseBean doSyncGetNext(FormplayerSyncScreen nextScreen,
                                           MenuSession menuSession) throws Exception {
        NotificationMessage notificationMessage = doSync(nextScreen);

        BaseResponseBean postSyncResponse = resolveFormGetNext(menuSession);
        if (postSyncResponse != null) {
            // If not null, we have a form or menu to redirect to
            postSyncResponse.setNotification(notificationMessage);
            return postSyncResponse;
        } else {
            // Otherwise, return use to the app root
            postSyncResponse = new BaseResponseBean(null, "Redirecting after sync", false, true);
            postSyncResponse.setNotification(notificationMessage);
            return postSyncResponse;
        }
    }

    private NotificationMessage doSync(FormplayerSyncScreen screen) throws Exception {
        ResponseEntity<String> responseEntity = syncRequester.makeSyncRequest(screen.getUrl(),
                screen.getBuiltQuery(),
                restoreFactory.getUserHeaders());
        if (responseEntity == null) {
            return new NotificationMessage("Session error, expected sync block but didn't get one.", true);
        }
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            // Don't purge for case claim
            restoreFactory.performTimedSync(false);
            return new NotificationMessage("Case claim successful.", false);
        } else {
            return new NotificationMessage(
                    String.format("Case claim failed. Message: %s", responseEntity.getBody()), true);
        }
    }

    /**
     * If we've encountered a QueryScreen and have a QueryDictionary, do the query
     * and update the session, screen, and notification message accordingly.
     * <p>
     * Will do nothing if this wasn't a query screen.
     */
    private NotificationMessage doQuery(FormplayerQueryScreen screen,
                                        MenuSession menuSession,
                                        Hashtable<String, String> queryDictionary) throws CommCareSessionException {
        log.info("Formplayer doing query with dictionary " + queryDictionary);
        NotificationMessage notificationMessage = null;
        screen.answerPrompts(queryDictionary);
        String responseString = queryRequester.makeQueryRequest(screen.getUriString(), restoreFactory.getUserHeaders());
        boolean success = screen.processResponse(new ByteArrayInputStream(responseString.getBytes(StandardCharsets.UTF_8)));
        if (success) {
            if (screen.getCurrentMessage() != null) {
                notificationMessage = new NotificationMessage(screen.getCurrentMessage(), false);
            }
        } else {
            notificationMessage = new NotificationMessage("Query failed with message " + screen.getCurrentMessage(), true);
        }
        Screen nextScreen = menuSession.getNextScreen();
        log.info("Next screen after query: " + nextScreen);
        return notificationMessage;
    }


    public BaseResponseBean resolveFormGetNext(MenuSession menuSession) throws Exception {
        menuSession.getSessionWrapper().syncState();
        if (menuSession.getSessionWrapper().finishExecuteAndPop(menuSession.getSessionWrapper().getEvaluationContext())) {
            menuSession.getSessionWrapper().clearVolatiles();
            menuSessionFactory.rebuildSessionFromFrame(menuSession);
            BaseResponseBean response = getNextMenu(menuSession);
            response.setSelections(menuSession.getSelections());
            return response;
        }
        return null;
    }

    protected static TreeReference getReference(SessionWrapper session, EntityDatum entityDatum) {
        EvaluationContext ec = session.getEvaluationContext();
        StackFrameStep stepToFrame = getStepToFrame(session);
        String caseId = stepToFrame.getValue();
        TreeReference reference = entityDatum.getEntityFromID(ec, caseId);
        if (reference == null) {
            throw new ApplicationConfigException(String.format("Could not create tile for case with ID %s " +
                    "because this case does not meet the criteria for the case list with ID %s.", caseId, entityDatum.getShortDetail()));
        }
        return reference;
    }

    public static EntityDetailListResponse getInlineDetail(MenuSession menuSession) {
        return getDetail(menuSession, true);
    }

    public static EntityDetailResponse getPersistentDetail(MenuSession menuSession) {
        EntityDetailListResponse detailListResponse = getDetail(menuSession, false);
        if (detailListResponse == null) {
            return null;
        }
        EntityDetailResponse[] detailList = detailListResponse.getEntityDetailList();
        if (detailList == null) {
            return null;
        }
        return detailList[0];
    }

    private static EntityDetailListResponse getDetail(MenuSession menuSession, boolean inline) {
        SessionWrapper session = menuSession.getSessionWrapper();
        StackFrameStep stepToFrame = getStepToFrame(session);
        if (stepToFrame == null) {
            return null;
        }
        EntityDatum entityDatum = session.findDatumDefinition(stepToFrame.getId());
        if (entityDatum == null) {
            return null;
        }
        String detailId;
        if (inline) {
            detailId = entityDatum.getInlineDetail();
        } else {
            detailId = entityDatum.getPersistentDetail();
        }
        if (detailId == null) {
            return null;
        }
        Detail persistentDetail = session.getDetail(detailId);
        TreeReference reference = getReference(session, entityDatum);

        EvaluationContext ec;
        if (inline) {
            ec = menuSession.getEvalContextWithHereFuncHandler();
            return new EntityDetailListResponse(persistentDetail.getFlattenedDetails(), ec, reference);
        } else {
            ec = new EvaluationContext(menuSession.getEvalContextWithHereFuncHandler(), reference);
            EntityDetailResponse detailResponse = new EntityDetailResponse(persistentDetail, ec);
            detailResponse.setHasInlineTile(entityDatum.getInlineDetail() != null);
            return new EntityDetailListResponse(detailResponse);
        }
    }

    private static StackFrameStep getStepToFrame(SessionWrapper session) {
        StackFrameStep stepToFrame = null;
        Vector<StackFrameStep> v = session.getFrame().getSteps();
        //So we need to work our way backwards through each "step" we've taken, since our RelativeLayout
        //displays the Z-Order b insertion (so items added later are always "on top" of items added earlier
        for (int i = v.size() - 1; i >= 0; i--) {
            StackFrameStep step = v.elementAt(i);

            if (SessionFrame.STATE_DATUM_VAL.equals(step.getType())) {
                //Only add steps which have a tile.
                EntityDatum entityDatum = session.findDatumDefinition(step.getId());
                if (entityDatum != null && entityDatum.getPersistentDetail() != null) {
                    stepToFrame = step;
                }
            }
        }
        return stepToFrame;
    }

    private NewFormResponse startFormEntry(MenuSession menuSession) throws Exception {
        if (menuSession.getSessionWrapper().getForm() != null) {
            NewFormResponse formResponseBean = generateFormEntrySession(menuSession);
            formResponseBean.setPersistentCaseTile(getPersistentDetail(menuSession));
            formResponseBean.setBreadcrumbs(menuSession.getBreadcrumbs());
            return formResponseBean;
        } else {
            return null;
        }
    }

    private String getAssertionFailure(MenuSession menuSession) {
        Text text = menuSession.getSessionWrapper().getCurrentEntry().getAssertions().getAssertionFailure(menuSession.getEvalContextWithHereFuncHandler());
        if (text != null) {
            return text.evaluate(menuSession.getEvalContextWithHereFuncHandler());
        }
        return null;
    }

    private NewFormResponse generateFormEntrySession(MenuSession menuSession) throws Exception {
        FormSession formEntrySession = menuSession.getFormEntrySession(formSendCalloutHandler, storageFactory);
        menuSessionRepo.save(new SerializableMenuSession(menuSession));
        NewFormResponse response = new NewFormResponse(formEntrySession);
        formSessionRepo.save(formEntrySession.serialize());
        return response;
    }

}
