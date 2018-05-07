/*
 * Copyright 2016-2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.galleon.clidemo;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jboss.galleon.ArtifactCoords;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.repomanager.FeaturePackRepositoryManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.wildfly.galleon.clidemo.web.SimpleSecuredServlet;
import org.wildfly.galleon.clidemo.web.SimpleServlet;

/**
 * Create 2 FP and install in local mvn repository
 *
 * @author jdenise@redhat.com
 */
public class SimpleDemo {

    public static final ArtifactCoords.Gav WEBAPP_SIMPLE_GAV = ArtifactCoords.newGav("org.wildfly.galleon.demo", "simplewebapp", "1.0.0.Final");
    public static final ArtifactCoords.Gav SECURITY_CONFIG_GAV = ArtifactCoords.newGav("org.wildfly.galleon.demo", "security-config", "1.0.0.Final");
    public static final ArtifactCoords.Gav WFSERVLET_GAV = ArtifactCoords.newGav("org.wildfly:wildfly-servlet-galleon-pack:13.0.0.Alpha1-SNAPSHOT");
    public static void main(String[] args) throws Exception {
        final Path tmpDir = IoUtils.createRandomTmpDir();
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "simple-demo.war");
        war.addClasses(SimpleServlet.class);
        war.addClasses(SimpleSecuredServlet.class);
        war.addAsWebInfResource(SimpleServlet.class.getClassLoader().getResource("jboss-web.xml"), "jboss-web.xml");
        war.addAsWebInfResource(SimpleServlet.class.getClassLoader().getResource("web.xml"), "web.xml");
        final Path warPath = tmpDir.resolve("simple-demo.war");
        try (OutputStream out = Files.newOutputStream(warPath)) {
            war.as(ZipExporter.class).exportTo(out);
        }

        // INSTALL WEB-APP
        FeaturePackRepositoryManager.newInstance(getMvnRepoPath()).
                installer().
                // FEATURE-PACK
                newFeaturePack(WEBAPP_SIMPLE_GAV)
                .addDependency("org.wildfly:wildfly-servlet-galleon-pack", FeaturePackConfig.builder(WFSERVLET_GAV)
                        .setInheritConfigs(false)
                        .setInheritPackages(false)
                        .build())
                .newPackage("org.jboss.galleon.demo.webapp", true)
                // Package content
                .addPath("standalone/deployments/" + warPath.getFileName(), warPath, true)
                .addDependency("org.wildfly:wildfly-servlet-galleon-pack", "org.wildfly.naming")
                .getFeaturePack()
                .getInstaller()
                .install();

        // INSTALL SECURITY CONFIG
        Path roles = tmpDir.resolve("application-roles.properties");
        Path users = tmpDir.resolve("application-users.properties");
        Files.copy(SimpleDemo.class.getClassLoader().getResourceAsStream("application-roles.properties"), roles);
        Files.copy(SimpleDemo.class.getClassLoader().getResourceAsStream("application-users.properties"), users);

        FeaturePackRepositoryManager.newInstance(getMvnRepoPath()).
                installer().
                // FEATURE-PACK
                newFeaturePack(SECURITY_CONFIG_GAV)
                .newPackage("org.jboss.galleon.demo.security", true)
                // Package content
                .addPath("standalone/configuration/application-roles.properties", roles, true)
                .addPath("standalone/configuration/application-users.properties", users, true)
                .getFeaturePack()
                .getInstaller()
                .install();
    }

    public static Path getMvnRepoPath() throws Exception {
        final Path path = Paths.get(System.getProperty("user.home"))
                .resolve(".m2")
                .resolve("repository");
        if (!Files.exists(path)) {
            throw new IllegalStateException("local maven repo does not exist");
        }
        return path;
    }
}
