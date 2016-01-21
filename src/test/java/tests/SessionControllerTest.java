package tests;

import application.SessionController;
import auth.HqAuth;
import beans.NewSessionBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import objects.SerializableSession;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import repo.SessionRepo;
import services.XFormService;
import utils.FileUtils;
import utils.TestContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestContext.class)
public class SessionControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private SessionRepo sessionRepoMock;

    @Autowired
    private XFormService xFormServiceMock;

    @InjectMocks
    private SessionController sessionController;


    @Before
    public void setUp() throws IOException {
        Mockito.reset(sessionRepoMock);
        Mockito.reset(xFormServiceMock);
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(sessionController).build();
    }

    @Test
    public void findSession() throws Exception {

        SerializableSession first = new SerializableSession();
        first.setId("1");
        first.setInstanceXml("<test1/>");

        when(sessionRepoMock.find("1")).thenReturn(first);

        ResultActions result = mockMvc.perform(get("/get_session/?id=1"));
        result.andExpect(status().isOk());

        //result.andExpect(model().attributeExists("id"));
        verify(sessionRepoMock, times(1)).find("1");
        verifyNoMoreInteractions(sessionRepoMock);
    }

    @Test
    public void findAllSessions() throws Exception {

        SerializableSession first = new SerializableSession();
        first.setId("1");
        first.setInstanceXml("<test1/>");
        SerializableSession second = new SerializableSession();
        second.setId("2");
        second.setInstanceXml("<test2/>");

        HashMap<Object, Object> sessions = new HashMap<Object, Object>();
        sessions.put("1", first);
        sessions.put("2", second);

        when(sessionRepoMock.findAll()).thenReturn(sessions);

        ResultActions result = mockMvc.perform(get("/sessions"));
        result.andExpect(status().isOk());

        //result.andExpect(model().attributeExists("id"));
        verify(sessionRepoMock, times(1)).findAll();
        verifyNoMoreInteractions(sessionRepoMock);
    }

    @Test
    public void newSession() throws Exception {

        Map<String, String> hqAuth = new HashMap<String, String>();
        hqAuth.put("type", "django-session");
        hqAuth.put("key", "123");

        NewSessionBean formRequest = new NewSessionBean("derp", "en", hqAuth);
        ObjectMapper converter = new ObjectMapper();
        String jsonBody = converter.writeValueAsString(formRequest);

        when(xFormServiceMock.getFormXml(anyString(), any(HqAuth.class)))
                .thenReturn(FileUtils.getFile(this.getClass(), "xforms/basic.xml"));

        MvcResult result = this.mockMvc.perform(
                post("/new_session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andReturn();

        //result.andExpect(model().attributeExists("id"));
        verify(sessionRepoMock, times(1)).save(Mockito.any(SerializableSession.class));
        verifyNoMoreInteractions(sessionRepoMock);
    }
}