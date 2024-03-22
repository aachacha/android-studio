/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.sdklib.repository.generated.sysimg.v4;

import com.android.repository.api.Repository;
import com.android.repository.impl.generated.v2.RepositoryType;
import com.android.sdklib.repository.meta.SysImgFactory;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;

/**
 * DO NOT EDIT This file was generated by xjc from sdk-sys-img-04.xsd. Any changes will be lost upon
 * recompilation of the schema. See the schema file for instructions on running xjc.
 *
 * <p>This object contains factory methods for each Java content interface and Java element
 * interface generated in the com.android.sdklib.repository.generated.sysimg.v4 package.
 *
 * <p>An ObjectFactory allows you to programmatically construct new instances of the Java
 * representation for XML content. The Java representation of XML content can consist of schema
 * derived interfaces and classes representing the binding of schema type definitions, element
 * declarations and model groups. Factory methods for each of these are provided in this class.
 */
@XmlRegistry
@SuppressWarnings("override")
public class ObjectFactory extends SysImgFactory {

    private static final QName _SdkSysImg_QNAME =
            new QName("http://schemas.android.com/sdk/android/repo/sys-img2/04", "sdk-sys-img");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes
     * for package: com.android.sdklib.repository.generated.sysimg.v4
     */
    public ObjectFactory() {}

    /** Create an instance of {@link SysImgDetailsType } */
    public SysImgDetailsType createSysImgDetailsType() {
        return new SysImgDetailsType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RepositoryType }{@code >}
     *
     * @param value Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement }{@code <}{@link RepositoryType }{@code >}
     */
    @XmlElementDecl(
            namespace = "http://schemas.android.com/sdk/android/repo/sys-img2/04",
            name = "sdk-sys-img")
    public JAXBElement<RepositoryType> createSdkSysImgInternal(RepositoryType value) {
        return new JAXBElement<RepositoryType>(_SdkSysImg_QNAME, RepositoryType.class, null, value);
    }

    public JAXBElement<Repository> generateSdkSysImg(Repository value) {
        return ((JAXBElement) createSdkSysImgInternal(((RepositoryType) value)));
    }

}
