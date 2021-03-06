/*
 * Copyright 2011 Revelytix, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mulgara.sparql.parser.cst;


/**
 * Used for expressions in functions.
 *
 * @created Oct 28, 2011
 * @author Paul Gearon
 * @licence <a href="{@docRoot}/../LICENCE.txt">Apache License, Version 2.0</a>
 */
public enum Modifier {
  star, plus, optional, none;
  
  public static Modifier get(String s) {
    if ("*".equals(s)) return star;
    if ("+".equals(s)) return plus;
    return none;
  }
}
