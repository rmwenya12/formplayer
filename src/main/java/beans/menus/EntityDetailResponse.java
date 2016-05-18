package beans.menus;

import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.util.cli.EntityDetailSubscreen;
import org.commcare.util.cli.EntityScreen;
import util.SessionUtils;

/**
 * Created by willpride on 4/13/16.
 */
public class EntityDetailResponse extends MenuSessionBean{
    private Object[] details;
    private Style[] styles;
    private String[] headers;

    public EntityDetailResponse(){}

    public EntityDetailResponse(EntityDetailSubscreen entityScreen){
        //TODO Get correct details title?
        this.setTitle("Details");
        this.details = entityScreen.getData();
        this.headers = entityScreen.getHeaders();
    }

    public Object[] getDetails() {
        return details;
    }

    public void setDetails(Object[] data) {
        this.details = data;
    }

    public Style[] getStyles() {
        return styles;
    }

    public void setStyles(Style[] styles) {
        this.styles = styles;
    }

    public String[] getHeaders() {
        return headers;
    }

    public void setHeaders(String[] headers) {
        this.headers = headers;
    }

    private void processStyles(Detail detail) {
        DetailField[] fields = detail.getFields();
        styles = new Style[fields.length];
        int i = 0;
        for (DetailField field : fields) {
            Style style = new Style(field);
            styles[i] = style;
            i++;
        }
    }
}
