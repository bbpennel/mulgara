/**
 * The contents of this file are subject to the Open Software License
 * Version 3.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.opensource.org/licenses/osl-3.0.txt
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 */
package org.mulgara.query.filter.value;

import java.net.URI;

import org.jrdf.graph.Node;
import org.mulgara.query.QueryException;
import org.mulgara.query.filter.TestContext;
import org.mulgara.query.filter.TestContextOwner;
import org.mulgara.query.rdf.BlankNodeImpl;
import org.mulgara.query.rdf.LiteralImpl;
import org.mulgara.query.rdf.URIReferenceImpl;
import static org.mulgara.query.rdf.XSD.*;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * Tests the string function class.
 *
 * @created Apr 10, 2008
 * @author Paul Gearon
 * @copyright &copy; 2008 <a href="http://www.topazproject.org/">The Topaz Project</a>
 * @licence <a href="{@docRoot}/../../LICENCE.txt">Open Software License v3.0</a>
 */
public class StrFnUnitTest extends TestCase {

  /**
   * Build the unit test.
   * @param name The name of the test
   */
  public StrFnUnitTest(String name) {
    super(name);
  }

  /**
   * Hook for test runner to obtain a test suite from.
   * @return The test suite
   */
  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTest(new StrFnUnitTest("testValues"));
    suite.addTest(new StrFnUnitTest("testVar"));
    return suite;
  }


  public void testValues() throws Exception {
    String str = "test";
    SimpleLiteral strLiteral = new SimpleLiteral(str);
    URI t = STRING_URI;
    ValueLiteral l = TypedLiteral.newLiteral(str, t, null);
    
    StrFn fn = new StrFn(l);

    assertTrue(fn.equals(strLiteral));
    assertFalse(fn.isBlank());
    assertFalse(fn.isIRI());
    assertTrue(fn.isLiteral());
    assertFalse(fn.isURI());
    assertEquals(SimpleLiteral.STRING_TYPE, fn.getType());
    assertEquals(SimpleLiteral.EMPTY, fn.getLang());

    l = TypedLiteral.newLiteral(str, null, "en");
    fn = new StrFn(l);
    assertTrue(fn.equals(strLiteral));
    assertFalse(fn.isBlank());
    assertFalse(fn.isIRI());
    assertTrue(fn.isLiteral());
    assertFalse(fn.isURI());

    l = TypedLiteral.newLiteral(new Integer(42));
    fn = new StrFn(l);
    assertTrue(fn.equals(new SimpleLiteral("42")));
    assertFalse(fn.isBlank());
    assertFalse(fn.isIRI());
    assertTrue(fn.isLiteral());
    assertFalse(fn.isURI());

    IRI i = new IRI(URI.create("foo:bar"));
    fn = new StrFn(i);
    fn.equals(new SimpleLiteral("foo:bar"));
  }

  public void testVar() throws Exception {
    String vName = "foo";
    Var v = new Var(vName);
    StrFn fn = new StrFn(v);
    
    URI xsdString = STRING_URI;
    URI xsdDouble = DOUBLE_URI;
    URI fooBar = URI.create("foo:bar");
    Node[][] rows = {
      new Node[] {new LiteralImpl("foo")},
      new Node[] {new LiteralImpl("foo", xsdString)},
      new Node[] {new LiteralImpl("5.0", xsdDouble)},
      new Node[] {new LiteralImpl("foo", "en")},
      new Node[] {new LiteralImpl("foo", fooBar)},
      new Node[] {new URIReferenceImpl(fooBar)},
      new Node[] {new BlankNodeImpl()},
    };
    TestContext c = new TestContext(new String[] {vName}, rows);
    c.beforeFirst();
    fn.setContextOwner(new TestContextOwner(c));

    // check the context setting
    fn.setCurrentContext(c);

    assertTrue(c.next());
    assertEquals("foo", fn.getValue());
    assertTrue(c.next());
    assertEquals("foo", fn.getValue());
    assertTrue(c.next());
    assertEquals("5.0", fn.getValue());
    assertTrue(c.next());
    assertEquals("foo", fn.getValue());
    assertTrue(c.next());
    assertEquals("foo", fn.getValue());
    assertTrue(c.next());
    assertEquals("foo:bar", fn.getValue());
    assertTrue(c.next());
    try {
      Object o = fn.getValue();
      fail("Got the string from a Blank Node: " + o);
    } catch (QueryException qe) { }
    assertFalse(c.next());
  }
  
}
