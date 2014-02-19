<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head> <title>config</title></head>
<body>
Need config:<br/>
<% if(request.getAttribute("applied")!=null) {
    %><div style="color: red">ERROR</div><%
} %>
<form action="/tst" method="post">
  <input type="hidden" name="command" value="configure"/>
  <textarea name="cfg" rows="10" cols="80"></textarea>
  <button id="go" type="submit">submit</button>
</form>

</body>
</html>