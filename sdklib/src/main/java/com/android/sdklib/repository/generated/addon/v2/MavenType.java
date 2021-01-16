package com.android.sdklib.repository.generated.addon.v2;

import com.android.repository.impl.generated.v2.TypeDetails;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.generated.common.v2.IdDisplayType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * DO NOT EDIT This file was generated by xjc from sdk-addon-02.xsd. Any changes will be lost upon
 * recompilation of the schema. See the schema file for instructions on running xjc.
 *
 * <p>Java class for mavenType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="mavenType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://schemas.android.com/repository/android/common/02}typeDetails"&gt;
 *       &lt;all&gt;
 *         &lt;element name="vendor" type="{http://schemas.android.com/sdk/android/repo/common/02}idDisplayType"/&gt;
 *       &lt;/all&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "mavenType",
        propOrder = {"vendor"})
@SuppressWarnings({"override", "unchecked"})
public class MavenType extends TypeDetails
        implements com.android.sdklib.repository.meta.DetailsTypes.MavenType {

    @XmlElement(required = true)
    protected IdDisplayType vendor;

    /**
     * Gets the value of the vendor property.
     *
     * @return possible object is {@link IdDisplayType }
     */
    public IdDisplayType getVendor() {
        return vendor;
    }

    /**
     * Sets the value of the vendor property.
     *
     * @param value allowed object is {@link IdDisplayType }
     */
    public void setVendorInternal(IdDisplayType value) {
        this.vendor = value;
    }

    public void setVendor(IdDisplay value) {
        setVendorInternal(((IdDisplayType) value));
    }

    public ObjectFactory createFactory() {
        return new ObjectFactory();
    }
}
