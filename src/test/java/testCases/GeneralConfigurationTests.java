package testCases;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static utilities.PdfMetaViewerConstants.APP_MAIN_TITLE;
import static utilities.PdfMetaViewerConstants.HEIGHT_APP;
import static utilities.PdfMetaViewerConstants.WIDTH_APP;

public class GeneralConfigurationTests {
    @Test(groups = "smoke")
    public void exposesProductIdentity() {
        assertThat(APP_MAIN_TITLE).isEqualTo("PDF Meta Viewer");
    }

    @Test(groups = "smoke")
    public void usesProductionWindowDimensions() {
        assertThat(WIDTH_APP).isEqualTo(1080);
        assertThat(HEIGHT_APP).isEqualTo(720);
    }
}
