package application;

import beans.AnswerQuestionRequestBean;
import beans.AnswerQuestionResponseBean;
import beans.NewSessionResponse;
import beans.NewSessionBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import objects.SerializableSession;
import objects.SessionList;
import org.commcare.api.json.AnswerQuestionJson;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.*;
import repo.SessionRepo;
import requests.NewFormRequest;
import services.XFormService;
import session.FormEntrySession;

import java.util.List;
import java.util.Map;

/**
 * Created by willpride on 1/12/16.
 */
@RestController
@EnableAutoConfiguration
public class SessionController {

    @Autowired
    private SessionRepo sessionRepo;

    @Autowired
    private XFormService xFormService;

    @RequestMapping("/new_session")
    public NewSessionResponse newFormResponse(@RequestBody String body) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        NewSessionBean newSessionBean = mapper.readValue(body, NewSessionBean.class);
        NewFormRequest newFormRequest = new NewFormRequest(newSessionBean, sessionRepo, xFormService);
        return newFormRequest.getResponse();
    }


    @RequestMapping(value = "/sessions", method = RequestMethod.GET, headers = "Accept=application/json")
    public @ResponseBody List<SerializableSession> findAllSessions() {

        Map<Object, Object> mMap = sessionRepo.findAll();
        SessionList sessionList = new SessionList();

        for(Object obj: mMap.values()){
            sessionList.add((SerializableSession)obj);
        }
        return sessionList;
    }

    @RequestMapping(value = "/get_session", method = RequestMethod.GET)
    @ResponseBody
    public SerializableSession getSession(@RequestParam(value="id") String id) {
        SerializableSession serializableSession = sessionRepo.find(id);
        return serializableSession;
    }


    @RequestMapping("/answer_question")
    public AnswerQuestionResponseBean answerQuestion(@RequestBody String body) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AnswerQuestionRequestBean answerQuestionBean = mapper.readValue(body, AnswerQuestionRequestBean.class);
        SerializableSession session = sessionRepo.find(answerQuestionBean.getSessionId());
        FormEntrySession formEntrySession = new FormEntrySession(session);
        JSONObject resp = AnswerQuestionJson.questionAnswerToJson(formEntrySession.getFormEntryController(),
                formEntrySession.getFormEntryModel(),
                answerQuestionBean.getAnswer(),
                answerQuestionBean.getFormIndex());
        session.setFormXml(formEntrySession.getFormXml());
        session.setInstanceXml(formEntrySession.getInstanceXml());
        sessionRepo.save(session);
        System.out.println("Saving Session: " + session);
        AnswerQuestionResponseBean responseBean = mapper.readValue(resp.toString(), AnswerQuestionResponseBean.class);
        return responseBean;

    }
}