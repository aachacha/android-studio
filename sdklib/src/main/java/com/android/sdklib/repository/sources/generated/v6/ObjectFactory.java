package com.android.sdklib.repository.sources.generated.v6;

import com.android.repository.impl.sources.generated.v1.SiteListType;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;

/**
 * DO NOT EDIT This file was generated by xjc from sdk-sites-list-6.xsd. Any changes will be lost
 * upon recompilation of the schema. See the schema file for instructions on running xjc.
 *
 * <p>This object contains factory methods for each Java content interface and Java element
 * interface generated in the com.android.sdklib.repository.sources.generated.v6 package.
 *
 * <p>An ObjectFactory allows you to programmatically construct new instances of the Java
 * representation for XML content. The Java representation of XML content can consist of schema
 * derived interfaces and classes representing the binding of schema type definitions, element
 * declarations and model groups. Factory methods for each of these are provided in this class.
 */
@XmlRegistry
@SuppressWarnings("override")
public class ObjectFactory {

    private static final QName _SdkAddonsList_QNAME =
            new QName("http://schemas.android.com/sdk/android/addons-list/6", "sdk-addons-list");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes
     * for package: com.android.sdklib.repository.sources.generated.v6
     */
    public ObjectFactory() {}

    /** Create an instance of {@link AddonSiteType } */
    public AddonSiteType createAddonSiteType() {
        return new AddonSiteType();
    }

    /** Create an instance of {@link SysImgSiteType } */
    public SysImgSiteType createSysImgSiteType() {
        return new SysImgSiteType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SiteListType }{@code >}
     *
     * @param value Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement }{@code <}{@link SiteListType }{@code >}
     */
    @XmlElementDecl(
            namespace = "http://schemas.android.com/sdk/android/addons-list/6",
            name = "sdk-addons-list")
    public JAXBElement<SiteListType> createSdkAddonsListInternal(SiteListType value) {
        return new JAXBElement<SiteListType>(_SdkAddonsList_QNAME, SiteListType.class, null, value);
    }

    public JAXBElement<com.android.repository.impl.sources.RemoteListSourceProviderImpl.SiteList>
            generateSdkAddonsList(
                    com.android.repository.impl.sources.RemoteListSourceProviderImpl.SiteList
                            value) {
        return ((JAXBElement) createSdkAddonsListInternal(((SiteListType) value)));
    }
}
