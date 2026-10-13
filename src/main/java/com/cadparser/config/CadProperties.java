package com.cadparser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cad")
public class CadProperties {

    private final Parse parse = new Parse();
    private final Dwg dwg = new Dwg();

    public Parse getParse() {
        return parse;
    }

    public Dwg getDwg() {
        return dwg;
    }

    public static class Parse {
        private int maxEntities = 200_000;

        public int getMaxEntities() {
            return maxEntities;
        }

        public void setMaxEntities(int maxEntities) {
            this.maxEntities = maxEntities;
        }
    }

    public static class Dwg {
        private String converter = "auto";
        private int timeoutSeconds = 90;
        private String odaConverterPath = "";
        private String dwg2dxfPath = "dwg2dxf";
        private String outputVersion = "ACAD2018";

        public String getConverter() {
            return converter;
        }

        public void setConverter(String converter) {
            this.converter = converter;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getOdaConverterPath() {
            return odaConverterPath;
        }

        public void setOdaConverterPath(String odaConverterPath) {
            this.odaConverterPath = odaConverterPath;
        }

        public String getDwg2dxfPath() {
            return dwg2dxfPath;
        }

        public void setDwg2dxfPath(String dwg2dxfPath) {
            this.dwg2dxfPath = dwg2dxfPath;
        }

        public String getOutputVersion() {
            return outputVersion;
        }

        public void setOutputVersion(String outputVersion) {
            this.outputVersion = outputVersion;
        }
    }
}
