package com.android.repository.impl.generated.generic.v2;

import com.android.repository.api.Repository;
import com.android.repository.impl.generated.v2.RepositoryType;
import com.android.repository.impl.meta.GenericFactory;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;

/**
 * DO NOT EDIT This file was generated by xjc from generic-02.xsd. Any changes will be lost upon
 * recompilation of the schema. See the schema file for instructions on running xjc.
 *
 * <p>This object contains factory methods for each Java content interface and Java element
 * interface generated in the com.android.repository.impl.generated.generic.v2 package.
 *
 * <p>An ObjectFactory allows you to programatically construct new instances of the Java
 * representation for XML content. The Java representation of XML content can consist of schema
 * derived interfaces and classes representing the binding of schema type definitions, element
 * declarations and model groups. Factory methods for each of these are provided in this class.
 */
@XmlRegistry
@SuppressWarnings("override")
public class ObjectFactory extends GenericFactory {

    private static final QName _Repository_QNAME =
            new QName("http://schemas.android.com/repository/android/generic/02", "repository");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes
     * for package: com.android.repository.impl.generated.generic.v2
     */
    public ObjectFactory() {}

    /** Create an instance of {@link GenericDetailsType } */
    public GenericDetailsType createGenericDetailsType() {
        return new GenericDetailsType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RepositoryType }{@code >}
     *
     * @param value Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement }{@code <}{@link RepositoryType }{@code >}
     */
    @XmlElementDecl(
            namespace = "http://schemas.android.com/repository/android/generic/02",
            name = "repository")
    public JAXBElement<RepositoryType> createRepositoryInternal(RepositoryType value) {
        return new JAXBElement<RepositoryType>(
                _Repository_QNAME, RepositoryType.class, null, value);
    }

    public JAXBElement<Repository> generateRepository(Repository value) {
        return ((JAXBElement) createRepositoryInternal(((RepositoryType) value)));
    }
}
