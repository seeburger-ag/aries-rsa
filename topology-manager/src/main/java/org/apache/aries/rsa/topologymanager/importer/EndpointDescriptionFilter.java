/*
 * <EndpointDescriptionFilter.java>
 *
 * created on 2026-01-28 by Mencho Menchev mailto:m.menchev@seeburger.com
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.aries.rsa.topologymanager.importer;


import java.util.Objects;

import org.osgi.service.remoteserviceadmin.EndpointDescription;


class EndpointDescriptionFilter
{
    private final String filter;
    private final EndpointDescription endpointDescription;


    public EndpointDescriptionFilter(String filter, EndpointDescription endpointDescription)
    {
        this.filter = filter;
        this.endpointDescription = endpointDescription;
    }


    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || getClass() != obj.getClass())
        {
            return false;
        }
        EndpointDescriptionFilter other = (EndpointDescriptionFilter)obj;
        return filter.equals(other.filter) && endpointDescription.equals(other.endpointDescription);
    }


    @Override
    public int hashCode()
    {
        return Objects.hash(filter, endpointDescription);
    }

}
