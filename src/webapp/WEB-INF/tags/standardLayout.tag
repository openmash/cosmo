<%--
/*
 * Copyright 2005-2006 Open Source Applications Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
--%>
<%@ include   file="/WEB-INF/jsp/taglibs.jsp"            %>
<%@ include file="/WEB-INF/jsp/tagfiles.jsp" %>

<%@ attribute name="prefix" 		%>
<%@ attribute name="showNav"        %>

<cosmo:staticbaseurl var="staticBaseUrl"/>

<c:if test="${empty showNav}">
  <c:set var="showNav" value="true"/>
  <cosmoui:user var="user"/>
</c:if>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" lang="en-US" xml:lang="en-US">
  <head>
    <title>
      <fmt:message key="${prefix}HeadTitle">
        <c:forEach var="p" items="${TitleParam}">
          <fmt:param value="${p}"/>
        </c:forEach>
      </fmt:message>
    </title>

    <cosmo:dojoBoilerplate/>
    <cosmo:globalCSSSafariHack/>

  </head>
  <body class="adminPage">

    <table border="0" cellpadding="0" cellspacing="0" width="100%">
      <tr>
        <td align="left" valign="top">
          <div class="lg">
            <a href="<c:url value="/account/view"/>">
              <img src="<c:url value="/cosmo_logo.gif"/>" alt="Cosmo Sharing Server"/>
            </a>
          </div>
        </td>
        <c:if test="${showNav}">
          <td align="right" valign="top">
            <!-- main navbar -->
            <div class="mdData">
              <fmt:message key="Layout.Nav.Main.Welcome"><fmt:param value="${user.username}"/></fmt:message>
              <authz:authorize ifAnyGranted="ROLE_USER">
                |
                <c:url var="homeUrl" value="/browse/${user.username}"/>
                <a href="${homeUrl}"><fmt:message key="Layout.Nav.Main.Home"/></a>
                |
                <c:url var="accountUrl" value="/account/view"/>
                <a href="${accountUrl}"><fmt:message key="Layout.Nav.Main.Account"/></a>
                |
                <c:url var="calendarUrl" value="/pim"/>
                <a href="${calendarUrl}"><fmt:message key="Layout.Nav.Main.Calendar"/></a>
              </authz:authorize>
              |
              <c:choose><c:when test="${fn:endsWith(body, '/help.jsp')}"><strong><fmt:message key="Layout.Nav.Main.Help"/></strong></c:when><c:otherwise><a href="<c:url value="/help"/>"><fmt:message key="Layout.Nav.Main.Help"/></a></c:otherwise></c:choose>
              |
              <c:url var="aboutUrl" value="/help/about"/>
              <a href="${aboutUrl}" onclick="simplePopUp('${aboutUrl}', 340, 280, false); return false;"><fmt:message key="Layout.Nav.Main.About"/></a>
              |
              <a href="<c:url value="/logout"/>">
                <fmt:message key="Layout.Nav.Main.LogOut"/>
              </a>
            </div>
            <!-- end main navbar -->
          </td>
        </c:if>
      </tr>
    </table>
    <hr noshade="noshade"/>
    <c:choose>
      <c:when test="${showNav}">
        <authz:authorize ifAllGranted="ROLE_ROOT">
          <!-- admin console navbar -->
          <div class="md">
            <fmt:message key="Layout.Nav.Console.Label"/>
            <c:choose><c:when test="${fn:endsWith(body, '/user/list.jsp')}"><strong><fmt:message key="Layout.Nav.Console.Users"/></strong></c:when><c:otherwise><a href="<c:url value="/admin/users"/>"><fmt:message key="Layout.Nav.Console.Users"/></a></c:otherwise></c:choose>
            |
            <c:choose><c:when test="${fn:endsWith(body, '/status/view.jsp')}"><strong><fmt:message key="Layout.Nav.Console.ServerStatus"/></strong></c:when><c:otherwise><a href="<c:url value="/admin/status"/>"><fmt:message key="Layout.Nav.Console.ServerStatus"/></a></c:otherwise></c:choose>
            <!-- end admin console navbar -->
          </div>
          <hr noshade="noshade"/>
        </authz:authorize>
      </c:when>
      <c:otherwise>
      </c:otherwise>
    </c:choose>
    <div class="md">
      <!-- page body -->
        <jsp:doBody/>
      <!-- end page body -->
    </div>
    <div id="debug"><div>
  </body>
</html>

