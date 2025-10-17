const { createApp } = Vue;
// --- 辅助函数 ---
// 将时间格式化函数提取出来，方便多个组件复用
const formatTime = (dateTimeString) => {
    if (!dateTimeString) return '';
    const date = new Date(dateTimeString);
    return date.toLocaleString();
};

// 创建帖子列表组件
const PostList = {
    template: ` <div class="posts-container">
                <h1>社区帖子</h1>
                <router-link :to="'/posts/' + post.id" class="post-item-link" v-for="post in posts" :key="post.id">
                    <div class="post-item">
                        <h3 class="post-title">{{ post.title }}</h3>
                        <p class="post-meta">作者: {{ post.authorUsername }} | 发布于: {{ formatTime(post.createTime) }}</p>
                        <p class="post-content">{{ post.content }}</p>
                    </div>
                </router-link>
                <p v-if="posts.length === 0">暂无帖子，快来发布第一篇吧！</p>
            </div>
        `,
    data() {
        return {
            posts: []
        };
    },
    methods: {
        // 将获取帖子列表的逻辑从主应用实例移到这里
        fetchPosts() {
            axios.get('http://localhost:8080/posts')
                .then(response => {
                    if (response.data.code === 0) {
                        this.posts = response.data.data;
                    } else {
                        alert('帖子加载失败: ' + response.data.message);
                    }
                })
                .catch(error => {
                    console.error('获取帖子列表出错:', error);
                    alert('网络错误，无法加载帖子列表。');
                });
        },
        // 组件内部也需要这个方法来格式化时间
        formatTime
    },
    // 当组件被创建时，自动获取帖子列表
    created() {
        this.fetchPosts();
    }
};

// 创建帖子详情组件
const PostDetail = {
    template: `
            <div class="post-detail-container">
                <!-- 提供一个返回列表的链接，提升用户体验 -->
                <div class="post-navigation">
                    <router-link to="/" class="back-link">&larr; 返回列表</router-link>
                    <!-- 编辑按钮，只有帖子作者才能看到并点击 -->
                    <!-- 将 v-if="canEdit" 移动到按钮的容器上 -->
                    <div v-if="canEdit" class="post-actions-inline">
                        <button class="btn-primary" @click="openEditModal">编辑</button>
                        <button class="btn-danger" @click="handleDeletePost">删除</button>
                    </div>
                </div>

                <!-- 如果帖子正在加载，显示提示信息 -->
                <div v-if="loading">正在加载帖子...</div>

                <!-- 如果出现错误，显示错误信息 -->
                <div v-else-if="error">{{ error }}</div>

                <!-- 成功获取到帖子数据后，显示详情 -->
                <div v-else-if="post">
                    <h1>{{ post.title }}</h1>
                    <p class="post-meta">作者: {{ post.authorUsername }} | 发布于: {{ formatTime(post.createTime) }}</p>
                    <div class="post-content-full">{{ post.content }}</div>
                </div>
                
                <div class="comments-section">
                <hr>
                <h3>评论 ({{ comments.length }})</h3>
                <!-- 发表评论表单 (仅登录用户可见) -->
                <div v-if="$root.loggedInUser" class="comment-form">
                    <textarea v-model="newCommentContent" placeholder="写下你的评论..."></textarea>
                    <button @click="handleCreateComment" class="btn-primary">发表评论</button>
                </div>
                 <!-- 未登录提示 -->
                <div v-else class="comment-login-prompt">
                    <a href="#" @click.prevent="$root.openLoginModal">登录</a>后参与评论
                </div>
                <!-- 评论列表 -->
                <div class="comments-list">
                    <div v-if="commentsLoading">正在加载评论...</div>
                    <div v-else-if="comments.length > 0">
                        <div class="comment-item" v-for="comment in comments" :key="comment.id">
                            <div class="comment-meta">
                                <strong>{{ comment.authorUsername }}</strong>
                                <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
                            </div>
                            <!-- 评论内容或编辑框 -->
                            <div v-if="comment.isEditing">
                                <textarea v-model="comment.editContent" class="comment-edit-textarea"></textarea>
                                <div class="comment-actions">
                                    <button class="btn-secondary btn-sm" @click="cancelEdit(comment)">取消</button>
                                    <button class="btn-primary btn-sm" @click="handleUpdateComment(comment)">保存</button>
                                </div>
                            </div>
                            <div v-else class="comment-content">
                                {{ comment.content }}
                            </div>
                            <!-- 评论操作按钮 (仅作者可见) -->
                            <div v-if="!comment.isEditing && isCommentOwner(comment)" class="comment-actions">
                                <button class="btn-link" @click="startEdit(comment)">编辑</button>
                                <button class="btn-link btn-link-danger" @click="handleDeleteComment(comment.id)">删除</button>
                            </div>
                        </div>
                    </div>
                    <p v-else>暂无评论，快来抢沙发吧！</p>
                </div>
                </div>
            </div>
                <!-- 编辑帖子模态框 -->
                <div class="modal-overlay" v-if="isEditModalVisible">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h2>编辑帖子</h2>
                            <button class="close-btn" @click="closeEditModal">&times;</button>
                        </div>
                        <div class="form-group-column">
                            <label for="edit-post-title">标题</label>
                            <input type="text" id="edit-post-title" v-model="editPost.title">
                        </div>
                        <div class="form-group-column">
                            <label for="edit-post-content">内容</label>
                            <textarea id="edit-post-content" v-model="editPost.content" rows="8"></textarea>
                        </div>
                        <div class="modal-actions">
                            <button class="btn-secondary" @click="closeEditModal">取消</button>
                            <button class="btn-primary" @click="handleEditPost">保存</button>
                        </div>
                    </div>
                </div>
        `,
    data() {
        return {
            post: null, // 用来存放从后端获取的单个帖子数据
            loading: true, // 加载状态
            error: null, // 错误信息
            isEditModalVisible: false, // 控制编辑模态框的可见性
            editPost: { // 存储编辑的帖子数据
                title: '',
                content: ''
            },
            comments: [],
            commentsLoading: true,
            newCommentContent: ''
        };
    },
    computed: {
        // 判断当前用户是否可以编辑这个帖子
        canEdit() {
            const root = this.$root;
            if (!root.loggedInUser || !this.post) {
                return false;
            }
            // 统一转换为数字进行比较
            return Number(root.loggedInUser.id) === Number(this.post.userId);
        }
    },
    methods: {
        formatTime,
        openEditModal() {
            // 将当前帖子数据填充到编辑表单中
            this.editPost.title = this.post.title;
            this.editPost.content = this.post.content;
            this.isEditModalVisible = true;
        },
        closeEditModal() {
            this.isEditModalVisible = false;
        },
        handleDeletePost() {
            if (!confirm('确定要删除这篇帖子吗？此操作不可恢复！')) {
                return;
            }
            
            const postId = this.post.id;
            axios.delete(`http://localhost:8080/posts/${postId}`)
                .then(response => {
                    if (response.data.code === 0) {
                        alert('删除成功！');
                        // 跳转回帖子列表页面
                        this.$router.push('/');
                    } else {
                        alert('删除失败: ' + response.data.message);
                    }
                })
                .catch(error => {
                    console.error('删除帖子出错:', error);
                    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
                        alert('认证失败，请重新登录后再试。');
                    } else {
                        alert('删除失败，请检查网络或联系管理员。');
                    }
                });
        },
        handleEditPost() {
            // 简单的前端校验
            if (!this.editPost.title.trim() || !this.editPost.content.trim()) {
                alert('标题和内容都不能为空！');
                return;
            }
            
            const postId = this.post.id;
            axios.put(`http://localhost:8080/posts/${postId}`, this.editPost)
                .then(response => {
                    if (response.data.code === 0) {
                        alert('更新成功！');
                        // 更新当前页面显示的帖子内容
                        this.post.title = this.editPost.title;
                        this.post.content = this.editPost.content;
                        this.closeEditModal();
                    } else {
                        alert('更新失败: ' + response.data.message);
                    }
                })
                .catch(error => {
                    console.error('更新帖子出错:', error);
                    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
                        alert('认证失败，请重新登录后再试。');
                    } else {
                        alert('更新失败，请检查网络或联系管理员。');
                    }
                });
        },
        isCommentOwner(comment) {
            const root = this.$root;
            return root.loggedInUser && Number(root.loggedInUser.id) === Number(comment.userId);
        },

        fetchComments() {
            this.commentsLoading = true;
            const postId = this.$route.params.id;
            axios.get(`http://localhost:8080/posts/${postId}/comments`)
                .then(response => {
                    if (response.data.code === 0) {
                        // 为每条评论添加编辑状态控制字段
                        this.comments = response.data.data.map(c => ({...c, isEditing: false, editContent: ''}));
                    } else {
                        alert('评论加载失败: ' + response.data.message);
                    }
                })
                .catch(error => {
                    console.error('获取评论出错:', error);
                    alert('网络错误，无法加载评论。');
                })
                .finally(() => {
                    this.commentsLoading = false;
                });
        },
        handleCreateComment() {
            if (!this.newCommentContent.trim()) {
                alert('评论内容不能为空！');
                return;
            }
            const postId = this.post.id;
            axios.post(`http://localhost:8080/posts/${postId}/comments`, {
                content: this.newCommentContent
            }).then(response => {
                if (response.data.code === 0) {
                    this.newCommentContent = '';
                    this.fetchComments();
                } else {
                    alert('评论失败: ' + response.data.message);
                }
            }).catch(this.handleApiError);
        },
        handleDeleteComment(commentId) {
            if (!confirm('确定要删除这条评论吗？此操作不可恢复！')) {
                return;
            }
            axios.delete(`http://localhost:8080/comments/${commentId}`)
                .then(response => {
                    if (response.data.code === 0) {
                        alert('删除成功！');
                        this.fetchComments();
                    } else {
                        alert('删除失败: ' + response.data.message);
                    }
                })
                .catch(this.handleApiError);
        },
        startEdit(comment) {
            comment.isEditing = true;
            comment.editContent = comment.content;
        },
        cancelEdit(comment) {
            comment.isEditing = false;
        },
        handleUpdateComment(comment) {
            if (!comment.editContent.trim()) {
                alert('评论内容不能为空！');
                return;
            }
            axios.put(`http://localhost:8080/comments/${comment.id}`, {
                content: comment.editContent
            }).then(response => {
                if (response.data.code === 0) {
                    comment.isEditing = false;
                    comment.content = comment.editContent;
                } else {
                    alert('更新失败: ' + response.data.message);
                }
            }).catch(this.handleApiError);
        },
        handleApiError(error) {
            console.error('API请求出错:', error);
            if (error.response && (error.response.status === 401 || error.response.status === 403)) {
                alert('认证失败，请重新登录后再试。');
            } else {
                alert('操作失败，请检查网络或联系管理员。');
            }
        }
    },
    created() {
        // this.$route.params.id 可以获取到URL中的动态部分，也就是帖子的ID
        const postId = this.$route.params.id;
        axios.get(`http://localhost:8080/posts/${postId}`)
            .then(response => {
                if (response.data.code === 0 && response.data.data) {
                    this.post = response.data.data;
                } else {
                    this.error = '加载失败: ' + (response.data.message || '帖子不存在');
                }
            })
            .catch(error => {
                console.error('获取帖子详情出错:', error);
                this.error = '网络错误，无法加载帖子详情。';
            })
            .finally(() => {
                this.loading = false; // 不管成功失败，加载都结束了
            });
        this.fetchComments();
    }
};

const MyPosts = {
    // 复用和PostList几乎一样的模板，只是标题不同
    template: `
        <div class="posts-container">
            <h1>我发布的帖子</h1>
            <router-link to="/" class="back-link">&larr; 返回社区帖子</router-link>
            <div v-if="loading" class="loading-indicator">正在加载您的帖子...</div>
            <div v-else-if="posts.length > 0">
                <router-link :to="'/posts/' + post.id" class="post-item-link" v-for="post in posts" :key="post.id">
                    <div class="post-item">
                        <h3 class="post-title">{{ post.title }}</h3>
                        <p class="post-meta">发布于: {{ formatTime(post.createTime) }}</p>
                         <p class="post-content-summary">{{ summarizeContent(post.content) }}</p>
                    </div>
                </router-link>
            </div>
            <p v-else class="empty-list-prompt">您还没有发布过任何帖子。</p>
        </div>
    `,
    data() {
        return { posts: [], loading: true };
    },
    methods: {
        // 主要区别在这里：调用 /user/posts 接口
        async fetchMyPosts() {
            this.loading = true;
            try {
                // axios的默认头已经携带了JWT
                const response = await axios.get('http://localhost:8080/user/posts');
                if (response.data.code === 0) {
                    this.posts = response.data.data;
                } else {
                    alert('加载您的帖子失败: ' + response.data.message);
                }
            } catch (error) {
                console.error('获取我的帖子出错:', error);
                alert('网络错误或认证失败，无法加载您的帖子。');
            } finally {
                this.loading = false;
            }
        },
        formatTime,
        summarizeContent(content) {
            if (!content) return '';
            return content.length > 150 ? content.substring(0, 150) + '...' : content;
        }
    },
    created() {
        this.fetchMyPosts();
    }
};


// 定义路由规则
const routes = [
    { path: '/', component: PostList },          // 根路径'/' 对应 PostList 组件
    { path: '/posts/:id', component: PostDetail }, // '/posts/...' 对应 PostDetail 组件, ':id'是动态参数
    { path: '/my-posts', component: MyPosts }
];

// 创建并配置路由实例
const router = VueRouter.createRouter({
    history: VueRouter.createWebHashHistory(), // 使用 hash 模式，URL会像这样: localhost:63342/#/posts/1
    routes,
});

const app = createApp({
    data() {
        return {
            isRegistrationModalVisible: false,
            isLoginModalVisible: false, // 控制登录模态框的可见性
            newUser: {
                username: '',
                email: '',
                password: '',
                confirmPassword: ''
            },
            loginUser: {
                username: '',
                password: ''
            },
            loggedInUser: null,
            isCreatePostModalVisible: false, // 控制发帖模态框
            newPost: {                      // 存储新帖子的数据
                title: '',
                content: ''
            },
            routerViewKey: 0, // 用于刷新router-view的key
            stompClient: null,                      // WebSocket STOMP 客户端实例
            notifications: [],                      // 存放通知列表
            unreadCount: 0,                         // 未读通知数量
            isNotificationDropdownVisible: false,   // 控制通知下拉框的显示
        }
    },
    methods: {
        formatTime,
        // --- 登录模态框控制 ---
        openLoginModal() {
            this.loginUser = { username: '', password: '' }; // 清空输入
            this.isLoginModalVisible = true;
        },
        closeLoginModal() {
            this.isLoginModalVisible = false;
        },
        // --- 注册模态框控制 ---
        openRegistrationModal() {
            this.newUser = { username: '', email: '', password: '', confirmPassword: '' };
            this.isRegistrationModalVisible = true;
        },
        closeRegistrationModal() {
            this.isRegistrationModalVisible = false;
        },
        // --- 功能逻辑 ---
        registerUser() {
            if (!this.newUser.username.trim() || !this.newUser.email.trim() || !this.newUser.password) {
                alert('请填写所有必填项！');
                return;
            }
            if (this.newUser.password !== this.newUser.confirmPassword) {
                alert('两次输入的密码不一致！');
                return;
            }
            const userData = {
                username: this.newUser.username,
                email: this.newUser.email,
                password: this.newUser.password
            };
            axios.post('http://localhost:8080/users/register', userData)
                .then(response => {
                    if (response.data.code === 0) {
                        alert('注册成功！');
                        this.closeRegistrationModal();
                        // 注册成功后可以自动弹出登录框，引导用户登录
                        this.openLoginModal();
                    } else {
                        alert('注册失败: ' + response.data.message);
                    }
                })
                .catch(error => {
                    console.error('注册请求出错:', error);
                    alert('注册失败，请查看控制台获取详情。');
                });
        },
        handleLogin() {
            if (!this.loginUser.username.trim() || !this.loginUser.password) {
                alert('用户名和密码不能为空！');
                return;
            }
            axios.post('http://localhost:8080/users/login', this.loginUser)
                .then(response => {
                    if (response.data.code === 0) {
                        alert('登录成功！');
                        const token = response.data.data;

                        // 1. 将JWT保存到 localStorage
                        localStorage.setItem('jwt-token', token);

                        // 2. 解析JWT并更新UI状态
                        this.loggedInUser = this.parseJwt(token);

                        // 3. (重要)为后续所有axios请求设置默认的认证头
                        axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;

                        this.connectWebSocket();
                        this.fetchNotifications();

                        this.closeLoginModal();
                    } else {
                        alert('登录失败: ' + response.data.message);
                    }
                }).catch(error => alert('登录失败，网络或服务器错误。'));
        },
        connectWebSocket() {
            if (!this.loggedInUser) return; // 未登录则不连接
            if (this.stompClient && this.stompClient.connected) {
                console.log("WebSocket 已连接。");
                return;
            }

            const socket = new SockJS('http://localhost:8080/ws');
            this.stompClient = Stomp.over(socket);

            // 在 STOMP 连接头中传递 JWT Token 进行认证
            const headers = {
                'Authorization': `Bearer ${localStorage.getItem('jwt-token')}`
            };

            this.stompClient.connect(headers, (frame) => {
                console.log('WebSocket 连接成功: ' + frame);

                // 订阅个人通知队列
                this.stompClient.subscribe('/user/queue/notifications', (message) => {
                    const newNotification = JSON.parse(message.body);
                    console.log("收到新通知:", newNotification);

                    // 在列表开头添加新通知，并更新未读数量
                    this.notifications.unshift(newNotification);
                    this.unreadCount++;

                    // 简单的桌面通知提示
                    if (Notification && Notification.permission === "granted") {
                        new Notification("LLM-Hub 新通知", {
                            body: newNotification.content,
                        });
                    }
                });
            }, (error) => {
                console.error('WebSocket 连接失败:', error);
                // 可以加入重连逻辑
            });
        },
        disconnectWebSocket() {
            if (this.stompClient) {
                this.stompClient.disconnect(() => {
                    console.log("WebSocket 已断开。");
                    this.stompClient = null;
                });
            }
        },
        fetchNotifications() {
            axios.get('http://localhost:8080/api/notifications/unread-count')
                .then(response => {
                    if(response.data.code === 0) {
                        this.unreadCount = response.data.data;
                    }
                });
        },
        toggleNotificationDropdown() {
            this.isNotificationDropdownVisible = !this.isNotificationDropdownVisible;
            // 打开下拉框时，获取完整的通知列表
            if (this.isNotificationDropdownVisible && this.notifications.length === 0) {
                axios.get('http://localhost:8080/api/notifications')
                    .then(response => {
                        if (response.data.code === 0) {
                            this.notifications = response.data.data;
                        }
                    });
            }
        },
        handleNotificationClick(notification) {
            // 如果通知未读，则调用API标记为已读
            if (!notification.read) {
                axios.post(`http://localhost:8080/api/notifications/${notification.id}/read`)
                    .then(response => {
                        if (response.data.code === 0) {
                            notification.read = true; // 前端同步状态
                            this.unreadCount--;
                        }
                    });
            }
            this.isNotificationDropdownVisible = false; // 点击后关闭下拉框
            // Vue Router 会处理后续的跳转
        },
        markAllAsRead() {
            // 简单实现：遍历所有未读通知并逐个标记
            this.notifications.forEach(n => {
                if (!n.read) {
                    this.handleNotificationClick(n);
                }
            });
        },
        logout() {
            // 1. 从 localStorage 中移除JWT
            localStorage.removeItem('jwt-token');

            // 2. 将UI状态重置为未登录
            this.loggedInUser = null;

            // 3. 移除axios的默认认证头
            delete axios.defaults.headers.common['Authorization'];

            // 4. 如果当前在需要认证的页面，则导航到主页
            if (this.$route.path === '/my-posts' || this.$route.path.startsWith('/posts/')) {
                this.$router.push('/');
            }

            alert('您已成功退出。');
        },
        parseJwt(token) {
            try {
                const base64Url = token.split('.')[1];
                const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
                const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
                    return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
                }).join(''));
                // 后端生成JWT时，把用户信息放在了 "claims" 字段里
                return JSON.parse(jsonPayload).claims;
            } catch (e) {
                console.error("解析Token失败:", e);
                return null;
            }
        },
        switchToRegisterModal() {
            this.closeLoginModal();
            this.openRegistrationModal();
        },

        openCreatePostModal() {
            // 每次打开都清空，保证是新帖子
            this.newPost = { title: '', content: '' };
            this.isCreatePostModalVisible = true;
        },
        closeCreatePostModal() {
            this.isCreatePostModalVisible = false;
        },
        handleCreatePost() {
            // 简单的前端校验
            if (!this.newPost.title.trim() || !this.newPost.content.trim()) {
                alert('标题和内容都不能为空！');
                return;
            }
            axios.post('http://localhost:8080/posts', this.newPost)
                .then(response => {
                    if (response.data.code === 0) {
                        alert('发布成功！');
                        this.closeCreatePostModal();
                        // 发布成功后刷新列表
                        // 通过改变 router-view 的 key 来强制重新渲染当前组件，
                        // 这会触发组件的 created 钩子，从而重新执行 fetchPosts()。
                        // 这是Vue中一种推荐的、强制刷新组件的技巧。
                        this.routerViewKey++;

                    } else {
                        alert('发布失败: ' + response.data.message);
                    }
                })
                .catch(error => {
                    console.error('发布帖子出错:', error);
                    // 如果是401或403错误，说明认证失败
                    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
                        alert('认证失败，请重新登录后再试。');
                        this.logout(); // 可以选择强制用户退出
                    } else {
                        alert('发布失败，请检查网络或联系管理员。');
                    }
                });
        }
    },
    created() {
        // // 1. 页面加载时，先获取帖子
        // this.fetchPosts();

        // 检查本地是否已存储JWT
        const token = localStorage.getItem('jwt-token');
        if (token) {
            // 如果有，解析它并更新UI
            const userData = this.parseJwt(token);
            if (userData) {
                this.loggedInUser = userData;
                // 同时，为axios设置认证头，以便后续请求能够携带JWT
                axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
                this.connectWebSocket();
                this.fetchNotifications();

                // 请求浏览器桌面通知权限
                if (Notification && Notification.permission !== "granted") {
                    Notification.requestPermission();
                }
            } else {
                // 如果token解析失败（可能是无效的或过期的），则清理掉
                localStorage.removeItem('jwt-token');
            }
        }
    }
});
app.use(router);

app.mount('#app');