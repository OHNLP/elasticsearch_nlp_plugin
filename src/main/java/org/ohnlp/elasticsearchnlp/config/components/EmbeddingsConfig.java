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

package org.ohnlp.elasticsearchnlp.config.components;

public class EmbeddingsConfig {
    private double score_weight;
    private int dim_size;
    private boolean ne_only;
    private boolean remove_wordpiece;

    public double getScore_weight() {
        return score_weight;
    }

    public void setScore_weight(double score_weight) {
        this.score_weight = score_weight;
    }

    public int getDim_size() {
        return dim_size;
    }

    public void setDim_size(int dim_size) {
        this.dim_size = dim_size;
    }

    public boolean isNe_only() {
        return ne_only;
    }

    public void setNe_only(boolean ne_only) {
        this.ne_only = ne_only;
    }

    public boolean isRemove_wordpiece() {
        return remove_wordpiece;
    }

    public void setRemove_wordpiece(boolean remove_wordpiece) {
        this.remove_wordpiece = remove_wordpiece;
    }
}
