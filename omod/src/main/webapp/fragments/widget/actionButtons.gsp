<% config.actionButtons.each { %>
    <% if (it.link) { %>
        <a href="${ it.link }"
          <% if (it.confirm) { %>
            onclick="return confirm('${ it.confirm }')"
          <% } %>
        />
    <% } %>
            <button id="${ it.id ?: ''}" type="${ it.type ?: ''}">${ it.label }</button>
    <% if (it.link) { %>
        </a>
    <% } %>
<% } %>

