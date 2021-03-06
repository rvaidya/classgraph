/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.classgraph;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Holds metadata about a package encountered during a scan. */
public class PackageInfo implements Comparable<PackageInfo>, HasName {
    /** Name of the package. */
    private String name;

    /** {@link AnnotationInfo} for any annotations on the package-info.class file, if present, else null. */
    private AnnotationInfoList annotationInfo;

    /** The parent package of this package. */
    private PackageInfo parent;

    /** The child packages of this package. */
    private Set<PackageInfo> children;

    /** Set of classes in the package. */
    private final Map<String, ClassInfo> memberClassNameToClassInfo = new HashMap<>();

    // -------------------------------------------------------------------------------------------------------------

    /** Deerialization constructor. */
    PackageInfo() {
    }

    /** Construct a PackageInfo object. */
    PackageInfo(final String packageName) {
        this.name = packageName;
    }

    /** The package name ("" for the root package). */
    @Override
    public String getName() {
        return name;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Add annotations found in a package descriptor classfile. */
    void addAnnotations(final AnnotationInfoList packageAnnotations) {
        // Currently only class annotations are used in the package-info.class file
        if (packageAnnotations != null && !packageAnnotations.isEmpty()) {
            if (this.annotationInfo == null) {
                this.annotationInfo = new AnnotationInfoList(packageAnnotations);
            } else {
                this.annotationInfo.addAll(packageAnnotations);
            }
        }
    }

    /**
     * Merge a {@link ClassInfo} object for a package-info.class file into this PackageInfo. (The same
     * package-info.class file may be present in multiple definitions of the package in different modules.)
     */
    void addClassInfo(final ClassInfo classInfo) {
        memberClassNameToClassInfo.put(classInfo.getName(), classInfo);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get a the named annotation on this package, or null if the package does not have the named annotation.
     * 
     * @param annotationName
     *            The annotation name.
     * @return An {@link AnnotationInfo} object representing the named annotation on this package, or null if the
     *         package does not have the named annotation.
     */
    public AnnotationInfo getAnnotationInfo(final String annotationName) {
        return getAnnotationInfo().get(annotationName);
    }

    /** Get any annotations on the {@code package-info.class} file. */
    public AnnotationInfoList getAnnotationInfo() {
        return annotationInfo == null ? AnnotationInfoList.EMPTY_LIST : annotationInfo;
    }

    /**
     * @param annotationName
     *            The name of an annotation.
     * @return true if this package has the named annotation.
     */
    public boolean hasAnnotation(final String annotationName) {
        return getAnnotationInfo().containsName(annotationName);
    }

    // -------------------------------------------------------------------------------------------------------------

    /** The parent package of this package, or null if this is the root package. */
    public PackageInfo getParent() {
        return parent;
    }

    /** The child packages of this package, or the empty list if none. */
    public PackageInfoList getChildren() {
        if (children == null) {
            return PackageInfoList.EMPTY_LIST;
        }
        final PackageInfoList childrenSorted = new PackageInfoList(children);
        // Ensure children are sorted
        Collections.sort(childrenSorted, new Comparator<PackageInfo>() {
            @Override
            public int compare(final PackageInfo o1, final PackageInfo o2) {
                return o1.name.compareTo(o2.name);
            }
        });
        return childrenSorted;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the {@link ClassInfo} object for the named class in this package, or null if the class was not found in
     * this module.
     */
    public ClassInfo getClassInfo(final String className) {
        return memberClassNameToClassInfo.get(className);
    }

    /** Get the {@link ClassInfo} objects for all classes that are members of this package. */
    public ClassInfoList getClassInfo() {
        return new ClassInfoList(new HashSet<>(memberClassNameToClassInfo.values()), /* sortByName = */ true);
    }

    private void getClassInfoRecursive(final Set<ClassInfo> reachableClassInfo) {
        reachableClassInfo.addAll(memberClassNameToClassInfo.values());
        for (final PackageInfo subPackageInfo : getChildren()) {
            subPackageInfo.getClassInfoRecursive(reachableClassInfo);
        }
    }

    /** Get the {@link ClassInfo} objects for all classes that are members of this package or a sub-package. */
    public ClassInfoList getClassInfoRecursive() {
        final Set<ClassInfo> reachableClassInfo = new HashSet<>();
        getClassInfoRecursive(reachableClassInfo);
        return new ClassInfoList(reachableClassInfo, /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the {@link PackageInfo} object for the named package, also creating {@link PackageInfo} objects for any
     * needed parent packages.
     */
    static PackageInfo getPackage(final String packageName,
            final Map<String, PackageInfo> packageNameToPackageInfo) {
        // Get or create PackageInfo object for this package
        PackageInfo packageInfo = packageNameToPackageInfo.get(packageName);
        if (packageInfo != null) {
            // PackageInfo object already exists for this package
            return packageInfo;
        }
        packageNameToPackageInfo.put(packageName, packageInfo = new PackageInfo(packageName));

        // Recursively create PackageInfo objects for parent packages, and connect package to parents
        if (!packageInfo.name.isEmpty()) {
            final int lastDotIdx = packageInfo.name.lastIndexOf('.');
            final String parentPackageName = lastDotIdx < 0 ? "" : packageInfo.name.substring(0, lastDotIdx);
            final PackageInfo parentPackageInfo = getPackage(parentPackageName, packageNameToPackageInfo);
            if (parentPackageInfo.children == null) {
                parentPackageInfo.children = new HashSet<>();
            }
            parentPackageInfo.children.add(packageInfo);
            packageInfo.parent = parentPackageInfo;
        }

        // Return the newly-created PackageInfo object
        return packageInfo;
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    public int compareTo(final PackageInfo o) {
        return this.name.compareTo(o.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof PackageInfo)) {
            return false;
        }
        return this.name.equals(((PackageInfo) o).name);
    }

    @Override
    public String toString() {
        return name;
    }
}
