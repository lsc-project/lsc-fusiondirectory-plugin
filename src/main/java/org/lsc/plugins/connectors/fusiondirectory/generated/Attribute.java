//
// Ce fichier a été généré par Eclipse Implementation of JAXB, v2.3.7 
// Voir https://eclipse-ee4j.github.io/jaxb-ri 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
// Généré le : 2025.04.08 à 05:01:33 PM CEST 
//


package org.lsc.plugins.connectors.fusiondirectory.generated;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;


/**
 * <p>Classe Java pour attribute complex type.
 * 
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.
 * 
 * <pre>
 * &lt;complexType name="attribute"&gt;
 *   &lt;simpleContent&gt;
 *     &lt;extension base="&lt;http://www.w3.org/2001/XMLSchema&gt;string"&gt;
 *       &lt;attribute name="multiple" type="{http://www.w3.org/2001/XMLSchema}boolean" default="false" /&gt;
 *       &lt;attribute name="passwordHash" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *     &lt;/extension&gt;
 *   &lt;/simpleContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "attribute", propOrder = {
    "value"
})
public class Attribute {

    @XmlValue
    protected String value;
    @XmlAttribute(name = "multiple")
    protected Boolean multiple;
    @XmlAttribute(name = "passwordHash")
    protected String passwordHash;

    /**
     * Obtient la valeur de la propriété value.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getValue() {
        return value;
    }

    /**
     * Définit la valeur de la propriété value.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Obtient la valeur de la propriété multiple.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public boolean isMultiple() {
        if (multiple == null) {
            return false;
        } else {
            return multiple;
        }
    }

    /**
     * Définit la valeur de la propriété multiple.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setMultiple(Boolean value) {
        this.multiple = value;
    }

    /**
     * Obtient la valeur de la propriété passwordHash.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Définit la valeur de la propriété passwordHash.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPasswordHash(String value) {
        this.passwordHash = value;
    }

}
