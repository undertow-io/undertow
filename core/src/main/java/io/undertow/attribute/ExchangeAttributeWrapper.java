package io.undertow.attribute;

/**
 * Interface that can be used to wrap an exchange attribute.
 *
 * @author Stuart Douglas
 */
public interface ExchangeAttributeWrapper {

    ExchangeAttribute wrap(ExchangeAttribute attribute);

}
