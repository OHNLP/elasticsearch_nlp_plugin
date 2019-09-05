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

package org.ohnlp.elasticsearchnlp.lucene;

import org.ohnlp.elasticsearchnlp.payloads.NLPPayload;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;

public class NLPTerm {
    private Term term;
    private BytesRef pyld;

    public NLPTerm(Term term) {
        this.term = term;
        this.pyld = new BytesRef(new NLPPayload().toBytes());
    }

    public NLPTerm(Term term, BytesRef pyld) {
        this.term = term;
        this.pyld = pyld;
    }

    public Term getTerm() {
        return term;
    }

    public void setTerm(Term term) {
        this.term = term;
    }

    public BytesRef getPyld() {
        return pyld;
    }

    public void setPyld(BytesRef pyld) {
        this.pyld = pyld;
    }
}
