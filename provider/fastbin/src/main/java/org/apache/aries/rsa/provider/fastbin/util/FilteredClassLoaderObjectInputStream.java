/*
 * FilteredClassLoaderObjectInputStream.java
 *
 * created at 2024-09-27 by t.neykov <t.neykov@seeburger.com>
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */

package org.apache.aries.rsa.provider.fastbin.util;


import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectStreamClass;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * This class is a subclass of {@link ClassLoaderObjectInputStream} that only allows a specific set of classes to be
 * deserialized. This is to prevent deserialization attacks.
 */
public class FilteredClassLoaderObjectInputStream extends ClassLoaderObjectInputStream
{
    /**
     * Property to disable secure deserialization. If this property is set to true, then the class will not throw an
     * exception if the class is not in the allowed classes list. This is useful for testing.
     */
    static final String PROPERTY_USE_INSECURE_DESERIALIZATION = "org.apache.aries.rsa.provider.fastbin.util.useInsecureDeserialization";
    static boolean useInsecureDeserialization = Boolean.getBoolean(PROPERTY_USE_INSECURE_DESERIALIZATION);

    private final Set<String> allowedClasses;
    private Predicate<String> allowedPackages;

    public FilteredClassLoaderObjectInputStream(InputStream s, Set<String> allowedClasses)
                    throws IOException
    {
        super(s);
        if (allowedClasses == null)
        {
            throw new IllegalArgumentException("allowedClasses must not be null");
        }

        this.allowedClasses = allowedClasses;
    }

    public FilteredClassLoaderObjectInputStream(InputStream inArg, Set<String> allowedClasses, Predicate<String> allowedPackages)
                    throws IOException
    {
        super(inArg);

        if (allowedClasses == null)
        {
            throw new IllegalArgumentException("allowedClasses must not be null");
        }

        this.allowedClasses = allowedClasses;
        this.allowedPackages = allowedPackages;
    }

    @Override
    protected Class< ? > resolveClass(ObjectStreamClass clsDescriptor)
                    throws IOException, ClassNotFoundException
    {
        String className = removeArrayMarkersFromClassName(clsDescriptor);

        if (!useInsecureDeserialization)
        {
            if (allowedClasses.contains(className))
            {
                return super.resolveClass(clsDescriptor);
            }
            if (allowedPackages != null && allowedPackages.test(className))
            {
                return super.resolveClass(clsDescriptor);
            }
            throw new InvalidClassException(className, "Invalid de-serialisation data. POSSIBLE ATTACK. Invalid class=" + className);
        }

        return super.resolveClass(clsDescriptor);
    }


    /**
     * Removes array markers from the class name. (could be more than one).
     * @param clsDescriptor
     * @return
     */
    private static String removeArrayMarkersFromClassName(ObjectStreamClass clsDescriptor)
    {
        String className = clsDescriptor.getName();
        int leadingBrackets = 0;
        while (className.charAt(leadingBrackets) == '[') {
            leadingBrackets++;
        }
        return className.substring(leadingBrackets);
    }


    public static class AllowlistPackagesPredicate implements Predicate<String>
    {
        private final List<String> allowedPackagesList;

        public AllowlistPackagesPredicate(List<String> allowedPackagesList)
        {
            this.allowedPackagesList = allowedPackagesList;
        }

        @Override
        public boolean test(String className)
        {
            return allowedPackagesList.stream().anyMatch(className::startsWith);
        }
    }
}
