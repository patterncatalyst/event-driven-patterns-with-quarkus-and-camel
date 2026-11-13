package com.patterncatalyst.shipping.projections;

import io.quarkus.test.junit.QuarkusTestProfile;

public class CamelProjectorProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "camel-projector";
    }
}
