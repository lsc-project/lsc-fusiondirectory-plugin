//
// Ce fichier a été généré par Eclipse Implementation of JAXB, v2.3.7 
// Voir https://eclipse-ee4j.github.io/jaxb-ri 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
// Généré le : 2025.04.08 à 05:01:33 PM CEST 
//


package org.lsc.plugins.connectors.fusiondirectory.generated;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.lsc.configuration.ServiceType;


/**
 * <p>Classe Java pour anonymous complex type.
 * 
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://lsc-project.org/XSD/lsc-core-2.1.xsd}serviceType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="entity" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="directory" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="pivot" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="base" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="filter" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="allFilter" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="oneFilter" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="cleanFilter" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="template" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="attributes" type="{http://lsc-project.org/XSD/lsc-fusiondirectory-plugin-1.0.xsd}attributes" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "entity",
    "directory",
    "pivot",
    "base",
    "filter",
    "allFilter",
    "oneFilter",
    "cleanFilter",
    "template",
    "attributes"
})
@XmlRootElement(name = "serviceSettings")
public class ServiceSettings
    extends ServiceType
{

    @XmlElement(required = true)
    protected String entity;
    protected String directory;
    protected String pivot;
    protected String base;
    protected String filter;
    protected String allFilter;
    protected String oneFilter;
    protected String cleanFilter;
    protected String template;
    protected Attributes attributes;

    /**
     * Obtient la valeur de la propriété entity.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEntity() {
        return entity;
    }

    /**
     * Définit la valeur de la propriété entity.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEntity(String value) {
        this.entity = value;
    }

    /**
     * Obtient la valeur de la propriété directory.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDirectory() {
        return directory;
    }

    /**
     * Définit la valeur de la propriété directory.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDirectory(String value) {
        this.directory = value;
    }

    /**
     * Obtient la valeur de la propriété pivot.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPivot() {
        return pivot;
    }

    /**
     * Définit la valeur de la propriété pivot.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPivot(String value) {
        this.pivot = value;
    }

    /**
     * Obtient la valeur de la propriété base.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBase() {
        return base;
    }

    /**
     * Définit la valeur de la propriété base.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBase(String value) {
        this.base = value;
    }

    /**
     * Obtient la valeur de la propriété filter.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFilter() {
        return filter;
    }

    /**
     * Définit la valeur de la propriété filter.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFilter(String value) {
        this.filter = value;
    }

    /**
     * Obtient la valeur de la propriété allFilter.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAllFilter() {
        return allFilter;
    }

    /**
     * Définit la valeur de la propriété allFilter.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAllFilter(String value) {
        this.allFilter = value;
    }

    /**
     * Obtient la valeur de la propriété oneFilter.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOneFilter() {
        return oneFilter;
    }

    /**
     * Définit la valeur de la propriété oneFilter.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOneFilter(String value) {
        this.oneFilter = value;
    }

    /**
     * Obtient la valeur de la propriété cleanFilter.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCleanFilter() {
        return cleanFilter;
    }

    /**
     * Définit la valeur de la propriété cleanFilter.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCleanFilter(String value) {
        this.cleanFilter = value;
    }

    /**
     * Obtient la valeur de la propriété template.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTemplate() {
        return template;
    }

    /**
     * Définit la valeur de la propriété template.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTemplate(String value) {
        this.template = value;
    }

    /**
     * Obtient la valeur de la propriété attributes.
     * 
     * @return
     *     possible object is
     *     {@link Attributes }
     *     
     */
    public Attributes getAttributes() {
        return attributes;
    }

    /**
     * Définit la valeur de la propriété attributes.
     * 
     * @param value
     *     allowed object is
     *     {@link Attributes }
     *     
     */
    public void setAttributes(Attributes value) {
        this.attributes = value;
    }

}
