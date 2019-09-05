/*
 *  Copyright: (c) 2019 Mayo Foundation for Medical Education and
 *  Research (MFMER). All rights reserved. MAYO, MAYO CLINIC, and the
 *  triple-shield Mayo logo are trademarks and service marks of MFMER.
 *
 *  Except as contained in the copyright notice above, or as used to identify
 *  MFMER as the author of this software, the trade names, trademarks, service
 *  marks, or product names of the copyright holder shall not be used in
 *  advertising, promotion or otherwise in connection with this software without
 *  prior written authorization of the copyright holder.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ohnlp.elasticsearchnlp.perf;

import opennlp.tools.util.Span;

import java.util.List;

/**
 * Axis aligned bounding box implementation. Code adapted from prior UIMA pipeline optimizations for general use cases.
 */
public interface AnnotationIndex {

    /**
     * The size at which a leaf should be constructed in the tree as opposed to the node
     */
    int MIN_LEAF_SIZE = 20;

    /**
     * Adds a new Span to the index
     *
     * @param ann The span to add
     */
    void insert(Span ann);

    /**
     * Removes a span from the index
     *
     * @param ann The span to remove
     */
    void remove(Span ann);

    /**
     * @param start The starting bound character position
     * @param end   The end bound character position
     * @return An ordered set of T in list form constrained by the given bounds ordered by starting position
     */
     List<Span> getCovering(int start, int end);

    /**
     * @param start The starting bound character position
     * @param end   The end bound character position
     * @return An ordered set of T in list form constraining the given bounds ordered by starting position
     */
    List<Span> getCovered(int start, int end);

    /**
     * @param start The starting bound character position
     * @param end   The end bound character position
     * @return An ordered set of T in list form that intersect with the supplied bounds ordered by starting position
     */
    List<Span> getCollisions(int start, int end);

    /**
     * Grows the index until it can contain a character count of a given size
     *
     * @param size The size to grow to (the actual end size may be larger)
     */
    void grow(int size);

    /**
     * Empties the index of its contents
     */
    void clear();
}
