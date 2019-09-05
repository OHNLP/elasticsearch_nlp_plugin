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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class AnnotationLeaf implements AnnotationIndex {

    private ArrayList<Span> annColl;
    private boolean dirty;

    AnnotationLeaf() {
        annColl = new ArrayList<Span>();
        dirty = false;
    }

    @Override
    public void insert(Span ann) {
        annColl.add(ann);
        dirty = true;
    }

    @Override
    public void remove(Span ann) {
        annColl.remove(ann);
    }

    @Override
    public List<Span> getCovering(int start, int end) {
        if (dirty) {
            sort();
        }
        LinkedList<Span> ret = new LinkedList<>();
        for (Span ann : annColl) {
            if (ann.getStart() <= start && ann.getEnd() >= end) {
                ret.add(ann);
            }
        }
        return ret;
    }

    @Override
    public List<Span> getCovered(int start, int end) {
        if (dirty) {
            sort();
        }
        LinkedList<Span> ret = new LinkedList<Span>();
        for (Span ann : annColl) {
            if (ann.getStart() >= start && ann.getEnd() <= end) {

                ret.add(ann);
            }
        }
        return ret;
    }

    @Override
    public List<Span> getCollisions(int start, int end) {
        if (dirty) {
            sort();
        }
        LinkedList<Span> ret = new LinkedList<>();
        for (Span ann : annColl) {
            if ((ann.getStart() <= start && ann.getEnd() > start) || (ann.getStart() >= start
                    && ann.getStart() <= end)) {
                ret.add(ann);

            }
        }
        return ret;
    }

    private void sort() {
        annColl.sort((o1, o2) -> {
            int startPos = o1.getStart() - o2.getStart();
            if (startPos != 0) {
                return startPos > 0 ? 1 : -1;
            }
            int endPos = o1.getEnd() - o2.getEnd();
            if (endPos != 0) {
                return endPos > 0 ? 1 : -1;
            }
            return 0;
        });
        dirty = false;
    }

    @Override
    public void grow(int size) {
        throw new UnsupportedOperationException("Growth of an index should be accomplished through the AnnotationRoot");
    }

    @Override
    public void clear() {
        this.annColl = null;
    }
}
