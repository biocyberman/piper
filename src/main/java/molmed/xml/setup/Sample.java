//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2015.03.26 at 03:54:41 PM CET
//


package molmed.xml.setup;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="samplename" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element ref="{setup.xml.molmed}library" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "samplename",
    "library"
})
@XmlRootElement(name = "sample")
public class Sample {

    @XmlElement(required = true)
    protected String samplename;
    @XmlElement(required = true)
    protected List<Library> library;

    /**
     * Gets the value of the samplename property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getSamplename() {
        return samplename;
    }

    /**
     * Sets the value of the samplename property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setSamplename(String value) {
        this.samplename = value;
    }

    /**
     * Gets the value of the library property.
     *
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the library property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getLibrary().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Library }
     *
     *
     */
    public List<Library> getLibrary() {
        if (library == null) {
            library = new ArrayList<Library>();
        }
        return this.library;
    }

}
