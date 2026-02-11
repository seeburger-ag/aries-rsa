/*
 * <ConcurrentHashSet.java>
 *
 * created on 2026-02-02 by Mencho Menchev mailto:m.menchev@seeburger.com
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.aries.rsa.core;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.service.remoteserviceadmin.ImportRegistration;


/**
 * A thread-safe Set implementation backed by a ConcurrentHashMap.
 * Used to provide easier access to the parent ImportRegistration.
 * @param <E>
 */
class ConcurrentHashSet<E> implements Set<E>
{
    private final Set<E> delegate = ConcurrentHashMap.newKeySet();
    private volatile ImportRegistration parentImportRegistration;

    public void setParentImportRegistration(ImportRegistration importRegistration) {
        this.parentImportRegistration = importRegistration;
    }

    public ImportRegistration getParentImportRegistration() {
        return parentImportRegistration;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return delegate.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return delegate.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
