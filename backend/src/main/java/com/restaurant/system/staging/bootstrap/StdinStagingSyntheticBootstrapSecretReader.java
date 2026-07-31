package com.restaurant.system.staging.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile(StagingSyntheticBootstrapGuard.BOOTSTRAP_PROFILE)
public class StdinStagingSyntheticBootstrapSecretReader
    implements StagingSyntheticBootstrapSecretReader {

    @Override
    public char[] readPassword() {
        try {
            String password = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8)
            ).readLine();
            if (password == null || password.isEmpty()) {
                throw new StagingSyntheticBootstrapException(
                    "STG005_BOOTSTRAP_PASSWORD_INVALID",
                    "A runtime password is required on standard input"
                );
            }
            return password.toCharArray();
        } catch (IOException exception) {
            throw new StagingSyntheticBootstrapException(
                "STG005_BOOTSTRAP_PASSWORD_READ_FAILED",
                "Runtime password could not be read from standard input"
            );
        }
    }
}
