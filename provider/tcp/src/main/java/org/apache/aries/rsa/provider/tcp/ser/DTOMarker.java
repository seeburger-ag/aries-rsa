package org.apache.aries.rsa.provider.tcp.ser;

import java.io.Serializable;
import java.util.Map;

import org.osgi.util.converter.Converter;
import org.osgi.util.converter.Converters;

public class DTOMarker implements Serializable {
    private static final long serialVersionUID = 2248068618419940217L;

    private String className;
    private Map<String, Object> content;
    
    @SuppressWarnings("unchecked")
    public DTOMarker(Object dto) {
        Converter converter = Converters.standardConverter();
        className = dto.getClass().getName();
        content = converter.convert(dto).sourceAsDTO().to(Map.class);
    }
    
    public Object getDTO(ClassLoader classLoader) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            Converter converter = Converters.standardConverter();
            return converter.convert(content).targetAsDTO().to(clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Exception deserializing DTO " + className, e);
        } 
    }
}
