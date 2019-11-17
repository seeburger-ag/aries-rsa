/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.rsa.topologymanager.importer;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;

public class ImportDiff {
    private Set<EndpointDescription> possible;
    private Set<ImportRegistration> imported;

    public ImportDiff(Set<EndpointDescription> possible, Set<ImportRegistration> imported) {
        this.possible = possible;
        this.imported = imported;
    }

    public Stream<ImportReference> getRemoved() {
        return imported.stream()
                .map(ImportRegistration::getImportReference)
                .filter(Objects::nonNull)
                .filter(ir -> !possible.contains(ir.getImportedEndpoint()));
    }
    
    public Stream<EndpointDescription> getAdded() {
        Set<EndpointDescription> importedEndpoints = importedEndpoints();
        return possible.stream()
                .filter(not(importedEndpoints::contains));
    }
    
    private Set<EndpointDescription> importedEndpoints() {
        return imported.stream()
            .map(ImportRegistration::getImportReference).filter(Objects::nonNull)
            .map(ImportReference::getImportedEndpoint).filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private static <T> Predicate<T> not(Predicate<T> t) {
        return t.negate();
    }
}
