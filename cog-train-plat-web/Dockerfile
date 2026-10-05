FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY index.html app.js styles.css user.html user-app.js user-styles.css /usr/share/nginx/html/
EXPOSE 80
