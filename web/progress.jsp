<%@ page contentType="application/json" language="java" %>
<%!
    private int counter;
%>
<%
    Integer counter = (Integer) application.getAttribute("counter");
    if(counter == null) {
        counter = 1;
    }
    counter++;
    application.setAttribute("counter", counter);
%>

{ "name": "Violet", "occupation": "character", "counter": "<%=counter%>" }