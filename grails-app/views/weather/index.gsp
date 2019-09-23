<!doctype html>
<html>
<head>
    <title>Weather analyze</title>
</head>
<body>

<h3>Generate Range report</h3>
<g:form action="index">
    From: <g:field type="text" name="from"/>
    To: <g:field type="text" name="to"/>
    Range: <g:field type="number" name="range"/>
    <g:submitButton name="Generate"/>
</g:form>

<h3>Generate Time report</h3>
<g:form action="index">
    From: <g:field type="text" name="from"/>
    To: <g:field type="text" name="to"/>
    Time: <g:field type="text" name="time"/>
    TimeRange: <g:field type="text" name="timeRange"/>
    Range: <g:field type="number" name="range"/>
    <g:submitButton name="Generate"/>
</g:form>
<br/>
<g:each var="file" in="${files}">
    <g:if test="${file.done}">
        <a href="/weather/file/${file.id}">${file.file}</a>
    </g:if>
    <g:else>
        ${file.file}
    </g:else>
    <br/>
</g:each>
</body>
</html>
