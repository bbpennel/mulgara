/*
 * Copyright 2010 Paul Gearon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mulgara.content.rdfa;

// Java 2 standard packages
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Map;

import javax.activation.MimeType;

// Third party packages
import org.apache.log4j.Logger;      // Apache Log4J
import org.jrdf.graph.BlankNode;     // JRDF
import org.jrdf.graph.Literal;
import org.jrdf.graph.Node;
import org.jrdf.graph.ObjectNode;
import org.jrdf.graph.PredicateNode;
import org.jrdf.graph.SubjectNode;
import org.jrdf.graph.Triple;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import net.rootdev.javardfa.ParserFactory.Format;
import net.rootdev.javardfa.StatementSink;
import net.rootdev.javardfa.ParserFactory;


// Locally written packages
import org.mulgara.content.Content;
import org.mulgara.content.NotModifiedException;
import org.mulgara.parser.MulgaraParserException;
import org.mulgara.query.TuplesException;
import org.mulgara.query.rdf.BlankNodeImpl;
import org.mulgara.query.rdf.LiteralImpl;
import org.mulgara.query.rdf.MimeTypes;
import org.mulgara.query.rdf.TripleImpl;
import org.mulgara.query.rdf.URIReferenceImpl;
import org.mulgara.resolver.spi.ResolverSession;
import org.mulgara.util.IntFile;
import org.mulgara.util.TempDir;

/**
 *
 * @created 2010-08-09
 * @author Paul Gearon
 */
class StatementParser implements Runnable, StatementSink {

  /** Logger. */
  private static final Logger logger = Logger.getLogger(StatementParser.class.getName());

  /** Text prefix for blank nodes. */
  @SuppressWarnings("unused")
  private static final String BLANK_PREFIX = "_:";

  /** The prefix that rdfa-java uses */
  private static final String RJ_PREFIX = "_:node";

  /** The period of time to wait in ms for the parser to provide some data. */
  private static final int TIMEOUT = 30000;

  /**
   * Maximum size for the {@link #triples} buffer. Any larger and the parser will
   * block and drain.
   */
  private static final int BUFFER_SIZE = 1000;

  /** Mapping between parsed blank node IDs and local node numbers. */
  private IntFile blankNodeIdMap;

  /** Mapping between blank node IDs and blank-node instances that haven't been stored. */
  private Map<Long,BlankNodeImpl> blankNodeInstMap = new HashMap<Long,BlankNodeImpl>();

  /** The resolverSession to create new internal identifiers for blank nodes. */
  private ResolverSession resolverSession;

  /** The data to be parsed and its metadata */
  private final Content content;

  /** The stream containing the data to be parsed. */
  private InputStream inputStream;

  /** The parser for the input stream. */
  private XMLReader reader;

  /** The base of the document. */
  private URI base;

  /** Resolves relative URIs and IRIs to absolute URIs/IRIs */
  private BasedResolver parseResolver;

  /** The queue of triples generated by the parser. */
  private LinkedBlockingQueue<Triple> triples = new LinkedBlockingQueue<Triple>(BUFFER_SIZE);

  /** A marker to indicate that the end of the data has been reached. */
  static final Triple TERMINATOR = new TripleImpl(null, null, null);

  /** The number of parsed statements */
  private long statementCount = 0;

  /** Indicates that parsing is complete */
  private volatile boolean finished = false;

  /** Used to asynchronously indicate an exception. */
  private Throwable exception = null;

  /** Thread used for parsing. This is the producer thread. */
  private Thread parserThread;

  /**
   * Sets up the sink to start receiving triples.
   * @param content Contains the data for parsing and its metadata.
   * @param resolverSession Access to the database for inserting data.
   */
  StatementParser(Content content, ResolverSession resolverSession) throws NotModifiedException, TuplesException {
    if (content == null) throw new IllegalArgumentException("Null \"content\" parameter");
    if (resolverSession == null) throw new IllegalArgumentException("Null \"resolverSession\" parameter");

    this.content = content;
    this.resolverSession = resolverSession;
    try {
      this.blankNodeIdMap = IntFile.open(TempDir.createTempFile("rdfaidmap", null), true);
      this.inputStream = content.newInputStream();
      this.base = content.getURI();
      parseResolver = new BasedResolver(content.getURIString());
      reader = ParserFactory.createReaderForFormat(this, getType(content), parseResolver);
    } catch (Exception e) {
      throw new TuplesException("Unable to obtain input stream from " + content.getURI(), e);
    }
  }

  /**
   * @return the number of statements parsed so far
   */
  long getStatementCount() {
    return statementCount;
  }

  /**
   * Do the parsing. This is the entry point of the parsing thread.
   */
  public void run() {
    parserThread = Thread.currentThread();
    Throwable t = null;

    try {
      reader.parse(new InputSource(inputStream));
      if (logger.isDebugEnabled()) logger.debug("Parsed RDFa on " + content.getURI());
      return;
    } catch (Throwable th) {
      logger.error("Error parsing RDFa", th);
      t = th;
    } finally {
      try {
        triples.put(TERMINATOR);
      } catch (InterruptedException e) {
        logger.error("Error ending RDFa parse", e);
        t = e;
      }
      if (t != null) exception = t;
      finished = true;
    }

    if (logger.isDebugEnabled()) logger.debug("Exception while parsing RDFa", exception);
  }

  public void start() {
    if (logger.isDebugEnabled()) logger.debug("Started RDFa document");
  }

  public void end() {
    if (logger.isDebugEnabled()) logger.debug("End RDFa document");
    finished = true;
  }

  public void addPrefix(String prefix, String uri) {
    if (logger.isDebugEnabled()) logger.debug("@prefix " + prefix + ": <" + uri + "> .");
  }

  public void setBase(String base) {
    try {
      if (base != null) parseResolver.setBase(base);
    } catch (IllegalArgumentException e) {
      logger.warn("Invalid base in RDFa file: " + base);
    }
  }

  /**
   * Adds an triple with a Literal as the object.
   * @param subject string form of the subject.
   * @param predicate string form of the predicate.
   * @param lex The lexical form of the literal in the object.
   * @param lang The language code of the literal in the object. May be <code>null</code>.
   * @param datatype The datatype of the literal in the object. May be <code>null</code>.
   */
  public void addLiteral(String subject, String predicate, String lex, String lang, String datatype) {
    try {
      enqueue((SubjectNode)toNode(subject), (PredicateNode)toNode(predicate), toLiteral(lex, lang, datatype));
    } catch (MulgaraParserException e) {
      logger.error("Unable to parse. " + e.getMessage());
      return;
    }
  }


  /**
   * Adds an triple with a URI or blank node as the object.
   * @param subject string form of the subject.
   * @param predicate string form of the predicate.
   * @param object string form of the object.
   */
  public void addObject(String subject, String predicate, String object) {
    try {
      enqueue((SubjectNode)toNode(subject), (PredicateNode)toNode(predicate), (ObjectNode)toNode(object));
    } catch (MulgaraParserException e) {
      logger.error("Unable to parse. " + e.getMessage());
      return;
    }
  }


  /**
   * Add a parsed triple to the queue.
   * @param subjectNode The subject of the triple.
   * @param predicateNode The predicate of the triple.
   * @param objectNode The object of the triple.
   */
  void enqueue(SubjectNode subjectNode, PredicateNode predicateNode, ObjectNode objectNode) {
    if (logger.isDebugEnabled()) {
      logger.debug("Parsed " + subjectNode + " " + predicateNode + " " + objectNode + " from " + content.getURI());
    }

    try {
      triples.put(new TripleImpl(subjectNode, predicateNode, objectNode));
    } catch (InterruptedException e) {
      throw new RuntimeException("Unable to record parsed triple", e);
    }
    statementCount++;
  }

  /**
   * Convert and validate an AST object into a node.
   *
   * @param text The text of the node that was parsed.
   * @return a {@link Node} formed from the text
   * @throws MulgaraParserException An unhandled element was encountered.
   */
  private Node toNode(String text) throws MulgaraParserException {
    if (text == null) return new URIReferenceImpl(base);

    if (text.startsWith(RJ_PREFIX)) return getBlankNode(text);
    return toUri(text);
  }

  /**
   * Creates a URIReference out of a string.
   * @param text The string to convert.
   * @return A new URIReference containing the URI from the string.
   * @throws MulgaraParserException The text was not a valid URI.
   */
  private Node toUri(String text) throws MulgaraParserException {
    try {
      return new URIReferenceImpl(new URI(text));
    } catch (URISyntaxException e) {
      throw new MulgaraParserException("Invalid URI: " + text, e);
    }
  }

  /**
   * Create a blank node from a URI with a blank node form.
   *
   * @param n The node to convert to an anonymous node.
   * @return An anonymous node that the node maps to.
   */
  private BlankNode getBlankNode(String n) throws MulgaraParserException {
    long anonId;
    try {
      anonId = Long.parseLong(n.substring(RJ_PREFIX.length()));
    } catch (NumberFormatException nfe) {
      throw new MulgaraParserException("Invalid blank node: " + n);
    }
    if (anonId < 0) throw new MulgaraParserException("Inexpected blank node format: " + n);

    synchronized (this) {
      // look up the id in the blank node map
      long internalId = blankNodeIdMap.getLong(anonId);

      // check if the node was found
      BlankNodeImpl blankNode;
      if (internalId == 0) {
        blankNode = blankNodeInstMap.get(anonId);
        if (blankNode == null) {
          blankNode = new BlankNodeImpl();
          blankNodeInstMap.put(anonId, blankNode);
        }
      } else {
        // Found the ID, so need to recreate the anonymous resource for it
        blankNode = new BlankNodeImpl(internalId);
      }

      return blankNode;
    }
  }

  /**
   * Creates a literal out of three components.
   * @param text The lexical value of the literal.
   * @param lang The language code of the literal, or <code>null</code> if not an
   *        untyped literal with a language code.
   * @param datatype The URI of the datatype of the literal, or <code>null</code>
   *        if an untyped literal.
   * @return A new literal.
   */
  Literal toLiteral(String text, String lang, String datatype) throws MulgaraParserException {
    if (datatype != null) {
      assert lang == null;
      try {
        return new LiteralImpl(text, new URI(datatype));
      } catch (URISyntaxException e) {
        throw new MulgaraParserException("Invalid datatype on literal: " + text + "^^" + datatype, e);
      }
    }
    if (lang != null) return new LiteralImpl(text, lang);
    return new LiteralImpl(text);
  }

  /**
   * If an exception occurred in the parser, throws a TuplesException that
   * wraps the exception.
   */
  private void checkForException() throws TuplesException {
    if (exception != null) {
      throw new TuplesException("Exception while reading " + content.getURIString(), exception);
    }
  }


  /**
   * Returns a new triple from the queue or null if there are no more triples.
   * @return The oldest triple in the queue.
   */
  Triple getTriple() throws TuplesException {
    checkForException();
    allocateBlankNodes();
    try {
      Triple result = triples.poll(TIMEOUT, TimeUnit.MILLISECONDS);
      if (result == null) throw new TuplesException("Timeout waiting for data from parser");
      return result;
    } catch (InterruptedException e) {
      throw new TuplesException("Unable to retrieve data from the parser", e);
    }
  }

  /**
   * Allocate the ids for the new blank nodes.
   */
  private void allocateBlankNodes() {
    try {
      for (Map.Entry<Long, BlankNodeImpl> entry : blankNodeInstMap.entrySet()) {
        resolverSession.localize(entry.getValue());     // This sets and returns the node ID
        blankNodeIdMap.putLong(entry.getKey(), entry.getValue().getNodeId());
      }
      blankNodeInstMap.clear();

    } catch (Exception le) {
      throw new RuntimeException("Unable to create blank node", le);
    }
  }

  /**
   * Stops the thread.
   */
  void terminate() {
    finished = true;
    if (parserThread != null && parserThread.isAlive()) parserThread.interrupt();
    triples.clear();
    try {
      triples.put(TERMINATOR);
    } catch (InterruptedException e) {
      exception = e;
    }
  }

  /**
   * Tests if the parse is complete.
   * @return <code>true</code> if parsing is over.
   */
  boolean isFinished() {
    return finished;
  }

  /**
   * Determine the type of parsing to be done, based on the content.
   * @param c The Content to be parsed.
   * @return Either <code>Format.XHTML</code> or <code>Format.HTML</code>.
   * @throws NotModifiedException 
   */
  private Format getType(Content c) throws NotModifiedException {
    MimeType t = c.getContentType();
    if (t != null) {
      if (MimeTypes.APPLICATION_XHTML.match(t)) return Format.XHTML;
      if (MimeTypes.TEXT_HTML.match(t)) return Format.HTML;
    }
    String loc = c.getURIString();
    if (loc != null) {
      if (loc.endsWith(RdfaContentHandler.XHTML_EXT)) return Format.XHTML;
      if (loc.endsWith(RdfaContentHandler.HTML_EXT)) return Format.HTML;
    }
    logger.warn("Guessing HTML for unknown MIME type: " + t);
    return Format.HTML;
  }
}
