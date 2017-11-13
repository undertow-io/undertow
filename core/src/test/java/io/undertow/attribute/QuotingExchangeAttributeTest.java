package io.undertow.attribute;

import org.junit.Assert;
import org.junit.Test;

public class QuotingExchangeAttributeTest {

    @Test
    public void testQuoting() {
        ExchangeAttribute delegate = new ConstantExchangeAttribute("test 'value'");
        ExchangeAttribute attribute = new QuotingExchangeAttribute(delegate);
        String value = attribute.readAttribute(null);
        Assert.assertEquals("'test \"'\"value\"'\"'", value);
    }

    @Test
    public void testNull() {
        ExchangeAttribute delegate = new ConstantExchangeAttribute(null);
        ExchangeAttribute attribute = new QuotingExchangeAttribute(delegate);
        String value = attribute.readAttribute(null);
        Assert.assertEquals("-", value);
    }

    @Test
    public void testEmpty() {
        ExchangeAttribute delegate = new ConstantExchangeAttribute("");
        ExchangeAttribute attribute = new QuotingExchangeAttribute(delegate);
        String value = attribute.readAttribute(null);
        Assert.assertEquals("-", value);
    }

    @Test
    public void testSingleQuote() {
        ExchangeAttribute delegate = new ConstantExchangeAttribute("'");
        ExchangeAttribute attribute = new QuotingExchangeAttribute(delegate);
        String value = attribute.readAttribute(null);
        Assert.assertEquals("'\"'\"'", value);
    }
}