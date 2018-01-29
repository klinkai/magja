package com.google.code.magja.soap;

import com.google.common.collect.Iterables;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMMetaFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.impl.llom.factory.OMLinkedListMetaFactory;
import org.apache.commons.beanutils.PropertyUtils;
import org.mozilla.javascript.Scriptable;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

public class SoapCallFactory {

    private static final String CORE_LOGIN = "login";
    private static final String CORE_CALL = "call";
    private static final String CORE_MULTI_CALL = "multiCall";
    private static final String CORE_LOGOUT = "endSession";
    private static final String SESSION_ID = "sessionId";
    private static final String RESOURCE_PATH = "resourcePath";
    private static final String ARGUMENTS = "args";
    private static final String MULTI_CALLS = "calls";
    private static final String MULTI_CALL_OPTIONS = "options";

    private OMFactory fac;
    private OMNamespace noNs;
    private OMNamespace mag;
    private OMNamespace ns2;
    private OMNamespace xsi;
    private OMNamespace encodingStyle;
    private OMNamespace xsd;
    private OMNamespace soapEnc;

    /**
     * @param omMetaFactory Useful if you want to pass it from OSGi Service Reference i.e.
     *                      using:
     *                      <p>
     *                      <pre>
     *                      &lt;reference interface="org.apache.axiom.om.OMMetaFactory"/&gt;
     *                               </pre>
     */
    public SoapCallFactory(OMMetaFactory omMetaFactory) {
        super();
        fac = omMetaFactory.getOMFactory();

        // Blank namespace for factory methods which require a namespace as
        // argument
        // but no namespace markers should be added to attributes or tags
        noNs = fac.createOMNamespace("", "");

        // Magento namespace
        mag = fac.createOMNamespace("urn:Magento", "ns1");

        // General namespaces, needed for soap
        xsi = fac.createOMNamespace("http://www.w3.org/2001/XMLSchema-instance", "xsi");
        encodingStyle = fac.createOMNamespace("http://schemas.xmlsoap.org/soap/encoding/", "encodingStyle");
        xsd = fac.createOMNamespace("http://www.w3.org/2001/XMLSchema", "xsd");
        ns2 = fac.createOMNamespace("http://xml.apache.org/xml-soap", "ns2");
        soapEnc = fac.createOMNamespace("http://schemas.xmlsoap.org/soap/encoding/", "SOAP-ENC");
    }

    public SoapCallFactory() {
        this(new OMLinkedListMetaFactory());
    }

    /**
     * Creates a Soap method for login
     *
     * @param user
     * @param password
     * @return the created method as axiom element
     */
    public OMElement createLoginCall(String user, String password) {
        OMElement method = fac.createOMElement(CORE_LOGIN, mag);

        OMElement paramUser = fac.createOMElement("username", mag);
        paramUser.addChild(fac.createOMText(paramUser, user));
        method.addChild(paramUser);

        OMElement paramKey = fac.createOMElement("apiKey", mag);
        paramKey.addChild(fac.createOMText(paramKey, password));
        method.addChild(paramKey);
        return method;
    }

    /**
     * Creates a soap method to end a session
     *
     * @param sessionId
     * @return the created method as axiom element
     */
    public OMElement createLogoutCall(String sessionId) {
        OMElement method = fac.createOMElement(CORE_LOGOUT, mag);
        OMElement paramSession = fac.createOMElement(SESSION_ID, mag);
        paramSession.addChild(fac.createOMText(paramSession, sessionId));
        method.addChild(paramSession);
        return method;
    }

    @SuppressWarnings("unchecked")
    public OMElement createCall(String sessionId, String methodPath, Object arg) {
        OMElement method = fac.createOMElement(CORE_CALL, mag);

        // Register XML namespaces in method
        method.declareNamespace(ns2);
        method.declareNamespace(xsi);
        method.declareNamespace(xsd);
        method.declareNamespace(encodingStyle);


        OMElement paramSession = fac.createOMElement(SESSION_ID, noNs);
        paramSession.addChild(fac.createOMText(paramSession, sessionId));
        method.addChild(paramSession);

        OMElement resourcePath = fac.createOMElement(RESOURCE_PATH, noNs);
        resourcePath.addChild(fac.createOMText(resourcePath, methodPath));
        method.addChild(resourcePath);

        OMElement paramArgs;
        if (arg instanceof Iterable) {
            Iterable<Object> args = (Iterable<Object>) arg;
            paramArgs = fac.createOMElement(ARGUMENTS, noNs);
            paramArgs.addAttribute("arrayType", ns2.getPrefix() + ":Map[" + Iterables.size(args) + "]", soapEnc);
            paramArgs.addAttribute("type", soapEnc.getPrefix() + ":Array", xsi);

            for (Object argument : args) {
                paramArgs.addChild(typedElement(noNs, "item", argument));
            }
        } else if (arg instanceof Map) {
            Map<String, Object> args = (Map<String, Object>) arg;
            paramArgs = typedElement(noNs, ARGUMENTS, arg);
            paramArgs.addAttribute("arrayType", ns2.getPrefix() + ":Map[" + args.size() + "]", soapEnc);
            paramArgs.addAttribute("type", soapEnc.getPrefix() + ":Array", xsi);

        } else if (arg != null && (arg.getClass().isArray() || arg.getClass().getName().equals("sun.org.mozilla.javascript.internal.NativeArray"))) {
            Object[] args = (Object[]) arg;
            paramArgs = fac.createOMElement(ARGUMENTS, noNs);
            paramArgs.addAttribute("arrayType", ns2.getPrefix() + ":Map" + args.length + "]", soapEnc);
            paramArgs.addAttribute("type", soapEnc.getPrefix() + ":Array", xsi);

            for (Object argument : args) {
                paramArgs.addChild(typedElement(noNs, "item", argument));
            }
        } else {
            paramArgs = typedElement(noNs, ARGUMENTS, arg);
        }
        method.addChild(paramArgs);

        return method;
    }

    /**
     * Dispatcher function which decides, how the axiom subtree for a value is
     * created.
     *
     * @param elementNs
     * @param name
     * @param value
     * @return
     */
    @SuppressWarnings("unchecked")
    private OMElement typedElement(OMNamespace elementNs, String name, Object value) {
        if (value == null) {
            /*
             * <category_id xsi:nil="true"/>
             * http://zvon.org/xxl/XMLSchemaTutorial/Output/ser_over_st0.html
             */
            OMElement element = fac.createOMElement(name, elementNs);
            element.addAttribute("nil", "true", xsi);
            return element;
        } else if (value instanceof String) {
            /*
             * Simple key-value map <item><key xsi:type="xsd:string">name</key><value
             * xsi:type="xsd:string">value</value></item>
             */
            return this.typedElement(elementNs, name, (String) value, xsd.getPrefix() + ":string");
        } else if (value instanceof Integer) {
            /*
             * Simple key-value map <item><key xsi:type="xsd:string">name</key><value
             * xsi:type="xsd:int">value</value></item>
             */
            return this.typedElement(elementNs, name, ((Integer) value).toString(), xsd.getPrefix() + ":int");
        } else if (value instanceof Long) {
            /*
             * Simple key-value map <item><key xsi:type="xsd:string">name</key><value
             * xsi:type="xsd:long">value</value></item>
             */
            return this.typedElement(elementNs, name, ((Long) value).toString(), xsd.getPrefix() + ":long");
        } else if (value instanceof Boolean) {
            /*
             * Simple key-value map <item><key xsi:type="xsd:string">name</key><value
             * xsi:type="xsd:int">value</value></item>
             */
            return this.typedElement(elementNs, name, ((Boolean) value).toString(), xsd.getPrefix() + ":boolean");
        } else if (value instanceof Date) {
            /*
             * Simple key-value map <item><key xsi:type="xsd:string">name</key><value
             * xsi:type="xsd:int">value</value></item>
             */
            SimpleDateFormat formater = new SimpleDateFormat("yyyy-MM-dd");
            return this.typedElement(elementNs, name, formater.format((Date) value), xsd.getPrefix() + ":string");
        } else if (value instanceof Double) {
            /*
             * Simple key-value map <item><key xsi:type="xsd:string">name</key><value
             * xsi:type="xsd:float">value</value></item>
             */
            return this.typedElement(elementNs, name, ((Double) value).toString(), xsd.getPrefix() + ":float");
        } else if (value instanceof BigDecimal) {
            /*
             * Simple key-value map with xsd:long value <item> <key
             * xsi:type="xsd:string">price</key> <value
             * xsi:type="xsd:float">9.2843</value> </item>
             */
            return this.typedElement(elementNs, name, value.toString(), xsd.getPrefix() + ":float");
        } else if (value instanceof String[]) {
            /*
             * String Array is represented by a list of items <item
             * SOAP-ENC:arrayType="xsd:string[length]" xsi:type="SOAP-ENC:Array">
             * <item xsi:type="xsd:string">string</item> <!-- more items if array
             * contains more entries --> </item>
             */
            String[] stringArray = (String[]) value;
            OMElement arrayArg = fac.createOMElement(name, elementNs);
            arrayArg.addAttribute("arrayType", xsd.getPrefix() + ":string[" + stringArray.length + "]", soapEnc);
            arrayArg.addAttribute("type", soapEnc.getPrefix() + ":Array", xsi);
            for (String item : stringArray) {
                arrayArg.addChild(typedElement(elementNs, "item", item));
            }
            return arrayArg;
        } else if (value instanceof Iterable) {
            Iterable<Object> list = (Iterable<Object>) value;
            OMElement arrayArg = fac.createOMElement(name, elementNs);
            arrayArg.addAttribute("arrayType", xsd.getPrefix() + ":ur-type[" + Iterables.size(list) + "]", soapEnc);
            arrayArg.addAttribute("type", soapEnc.getPrefix() + ":Array", xsi);
            for (Object item : list) {
                arrayArg.addChild(typedElement(elementNs, "item", item));
            }
            return arrayArg;

        } else if (value instanceof ArrayItemMap) {
            /*
             * Map is represented by a list of key-value pairs <item
             * xsi:type="SOAP-XML:Map"> <item><key
             * xsi:type="xsd:string">name-of-key</key><value
             * xsi:type="xsd:XX">value</value></item> <!-- more items if map contains
             * more entries--> </item>
             */
            ArrayItemMap argMap = (ArrayItemMap) value;
            OMElement mapArg = fac.createOMElement(name, elementNs);
            mapArg.addAttribute("type", ns2.getPrefix() + ":Map", xsi);
            for (Object key : argMap.getItems().keySet()) {
                mapArg.addChild(keyValue(key, argMap.getItems().get(key)));
            }
            return mapArg;
        } else if (value instanceof Map) {
            /**
             * Map is represented by a list of key-value pairs.
             *
             * <pre>
             * <item xsi:type="SOAP-XML:Map">
             * 		<item>
             * 			<key xsi:type="xsd:string">name-of-key</key>
             * 			<value xsi:type="xsd:XX">value</value>
             * 		</item> <!-- more items if map contains more entries-->
             * </item>
             * </pre>
             */
            Map<String, Object> argMap = (Map<String, Object>) value;
            OMElement mapArg = fac.createOMElement(name, elementNs);
            mapArg.addAttribute("type", ns2.getPrefix() + ":Map", xsi);
            for (Entry<String, Object> entry : argMap.entrySet()) {
                mapArg.addChild(keyValue(entry.getKey(), entry.getValue()));
            }
            return mapArg;
        } else if (value instanceof Scriptable && value.getClass().getSimpleName().equals("NativeArray")) {
            /*
             * Handle javascript native array object, known issue in MacOS JDK,
             * behaviour is the same as List object
             */
            // sun.org.mozilla.javascript.internal.NativeArray jsArray =
            // (sun.org.mozilla.javascript.internal.NativeArray) value;
            Scriptable jsArray = (Scriptable) value;
            OMElement arrayArg = fac.createOMElement(name, elementNs);
            arrayArg.addAttribute("arrayType", xsd.getPrefix() + ":ur-type[" + jsArray.getIds().length + "]", soapEnc);
            arrayArg.addAttribute("type", soapEnc.getPrefix() + ":Array", xsi);
            for (int i = 0; i < jsArray.getIds().length; i++) {
                arrayArg.addChild(typedElement(elementNs, "item", jsArray.get(i, jsArray)));
            }
            return arrayArg;
        } else if (value instanceof Scriptable) {
            /*
             * Handle map object in javascript
             */
            Scriptable natObj = (Scriptable) value;

            OMElement mapArg = fac.createOMElement(name, elementNs);
            mapArg.addAttribute("type", ns2.getPrefix() + ":Map", xsi);
            for (Object obj : natObj.getIds()) {
                mapArg.addChild(keyValue(obj.toString(), natObj.get(obj.toString(), null)));
            }
            return mapArg;
        } else if (value instanceof Serializable) {
            /**
             * Map is represented by a list of key-value pairs.
             *
             * <pre>
             * <item xsi:type="SOAP-XML:Map">
             * 		<item>
             * 			<key xsi:type="xsd:string">name-of-key</key>
             * 			<value xsi:type="xsd:XX">value</value>
             * 		</item> <!-- more items if map contains more entries-->
             * </item>
             * </pre>
             */
            OMElement mapArg = fac.createOMElement(name, elementNs);
            mapArg.addAttribute("type", ns2.getPrefix() + ":Map", xsi);
            PropertyDescriptor[] properties = PropertyUtils.getPropertyDescriptors(value);
            for (PropertyDescriptor property : properties) {
                if (property.getName().equals("class"))
                    continue;
                try {
                    mapArg.addChild(keyValue(property.getName(), PropertyUtils.getProperty(value, property.getName())));
                } catch (Exception e) {
                    throw new MagentoSoapException("Cannot convert JavaBean property " + property.getName() + " to Map entry", e);
                }
            }
            return mapArg;
        }
        throw new MagentoSoapException("keyValue not implemented for " + value.getClass().getName() + " " + name + "=" + value);
    }

    /**
     * Create a simple element with a xsi:type attribute
     *
     * @param elementNs
     * @param name
     * @param value
     * @param valueType
     * @return
     */
    private OMElement typedElement(OMNamespace elementNs, String name, String value, String valueType) {
        OMElement element = fac.createOMElement(name, elementNs);
        element.addAttribute("type", valueType, xsi);
        element.addChild(fac.createOMText(element, value));
        return element;
    }

    /**
     * Creates an key-value elements contained by an item-element
     *
     * @param key
     * @param value
     * @return
     */
    private OMElement keyValue(Object key, Object value) {
        OMElement item = fac.createOMElement("item", noNs);
        item.addChild(typedElement(noNs, "key", key));
        item.addChild(typedElement(noNs, "value", value));
        return item;
    }
}
