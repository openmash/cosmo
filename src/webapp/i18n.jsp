<%--
/*
 * Copyright 2005 Open Source Applications Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
--%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core"        prefix="c"      %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt"         prefix="fmt"    %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions"   prefix="fn"     %>
<fmt:setBundle basename="PimMessageResources"/>
 
var localText = [];

function getText(str) {
    return localText[str];
}

<fmt:bundle basename="PimMessageResources">
	<c:forEach var="key" items="${messages}">
	    localText["${key}"] = "<fmt:message bundle="PimMessageResources" key="${key}"/>";
	</c:forEach>
</fmt:bundle>
<fmt:message  key="App.Sunday"/>