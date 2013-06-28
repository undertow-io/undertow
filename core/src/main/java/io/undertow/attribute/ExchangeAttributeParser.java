package io.undertow.attribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Attribute parser for exchange attributes. This builds an attribute from a string definition.
 *
 * This uses a service loader mechanism to allow additional token types to be loaded. Token definitions are loaded
 * from the provided class loader.
 *
 *
 * @see ExchangeAttributes#parser(ClassLoader)
 * @author Stuart Douglas
 */
public class ExchangeAttributeParser {

    private final List<ExchangeAttributeBuilder> buiders;

    ExchangeAttributeParser(final ClassLoader classLoader) {
        ServiceLoader<ExchangeAttributeBuilder> loader = ServiceLoader.load(ExchangeAttributeBuilder.class, classLoader);
        final List<ExchangeAttributeBuilder> builders = new ArrayList<ExchangeAttributeBuilder>();
        for (ExchangeAttributeBuilder instance : loader) {
            builders.add(instance);
        }
        this.buiders = Collections.unmodifiableList(builders);

    }

    public ExchangeAttribute parser(final String token) {
        for (final ExchangeAttributeBuilder builder : buiders) {
            ExchangeAttribute res = builder.build(token);
            if (res != null) {
                return res;
            }
        }
        return new ConstantExchangeAttribute(token);
    }

}
