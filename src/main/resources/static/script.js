const { createApp } = Vue;

const formatTime = (time) => time ? new Date(time).toLocaleString() : '';

// 帖子列表组件
const PostList = {
    template: `
        <div class="post-container">
            <!-- 加载状态 -->
            <div v-if="loading" class="loading-state">
                <div class="spinner"></div>
                <p>正在加载内容...</p>
            </div>

            <!-- 数据列表 -->
            <div v-else-if="posts.length > 0" class="post-grid">
                <router-link v-for="post in posts" :key="post.id" :to="'/posts/' + post.id" class="post-card">
                    <h2 class="post-title">{{ post.title }}</h2>
                    <div class="post-info">
                        <span>👤 {{ post.authorUsername }}</span>
                        <span>🕒 {{ formatTime(post.createTime) }}</span>
                        <span>👍 {{ post.likeCount || 0 }}</span>
                    </div>
                    <p class="post-excerpt">{{ post.content.substring(0, 100) }}...</p>
                </router-link>
            </div>

            <!-- 空状态反馈 -->
            <div v-else class="empty-placeholder">
                <div class="empty-icon">📭</div>
                <h3>这里空空如也</h3>
                <p>{{ isMyPosts ? '你还没有发布过任何帖子，快去分享你的见解吧！' : '社区暂时还没有帖子。' }}</p>
                <button v-if="isMyPosts" class="btn-primary" @click="$root.openCreatePostModal">
                    ✨ 立即发布首篇帖子
                </button>
            </div>
        </div>
    `,
    data: () => ({
        posts: [],
        loading: true
    }),
    computed: {
        // 判断当前是否在“我的创作”页面
        isMyPosts() {
            return this.$route.path === '/my-posts';
        }
    },
    methods: {
        async fetchPosts() {
            this.loading = true;
            try {
                // 根据路由选择不同的 API 路径
                const url = this.isMyPosts
                    ? 'http://localhost:8080/user/posts'
                    : 'http://localhost:8080/posts';

                const res = await axios.get(url);
                if (res.data.code === 0) {
                    this.posts = res.data.data;
                }
            } catch (error) {
                console.error('获取帖子失败:', error);
            } finally {
                this.loading = false;
            }
        },
        formatTime
    },
    // 关键：监听路由变化。当用户在导航栏点击时，手动触发数据重新加载
    watch: {
        '$route': {
            handler: 'fetchPosts',
            immediate: true // 初始载入时也执行一次
        }
    }
};

// 帖子详情组件 (集成点赞和 AI)
const PostDetail = {
    template: `
        <div class="post-detail" v-if="post">
            <div class="detail-header">
                <h1 v-if="!isEditing">{{ post.title }}</h1>
                <input v-else v-model="editingPost.title" class="edit-title-input" placeholder="请输入标题" />
                <div class="detail-meta">
                    作者: {{ post.authorUsername }} | 发布于: {{ formatTime(post.createTime) }}
                </div>
            </div>

            <div v-if="!isEditing" class="ai-summary-box" :class="{ 'loading': aiLoading, 'fallback': aiStatus === 'FALLBACK', 'error': aiStatus === 'ERROR' }">
                <div class="ai-header">
                    <span class="ai-badge">AI 助手总结</span>
                    <button class="btn-ai" @click="getAiSummary" :disabled="aiLoading">
                        {{ aiLoading ? '生成中...' : (aiSummary ? '重新生成' : '一键总结') }}
                    </button>
                </div>
                <div class="ai-content">
                    <p v-if="aiSummary" :class="{ 'ai-fallback-text': aiStatus === 'FALLBACK', 'ai-error-text': aiStatus === 'ERROR' }">
                        {{ aiSummary }}
                    </p>
                    <p v-else-if="aiLoading" class="pulse">AI 正在深度阅读中，请稍候...</p>
                    <p v-else class="placeholder">点击上方按钮，让 AI 为你总结核心观点</p>
                </div>
            </div>

            <div v-if="!isEditing" class="post-body">{{ post.content }}</div>
            <textarea v-else v-model="editingPost.content" class="edit-content-textarea" placeholder="请输入内容"></textarea>

            <div class="post-footer">
                <button class="like-btn" :class="{ 'active': hasLiked }" @click="toggleLike">
                    <span class="heart">{{ hasLiked ? '❤️' : '🤍' }}</span> {{ likeCount }}
                </button>
                <div v-if="isOwner" class="owner-actions">
                    <button v-if="!isEditing" class="btn-text btn-edit" @click="startEditPost">编辑</button>
                    <button v-if="!isEditing" class="btn-text" @click="handleDelete">删除</button>
                    <template v-else>
                        <button class="btn-text btn-cancel" @click="cancelEditPost">取消</button>
                        <button class="btn-text btn-confirm" @click="saveEditPost">保存</button>
                    </template>
                </div>
            </div>

            <section v-if="!isEditing" class="comments">
                <h3>交流评论</h3>
                <div v-if="$root.loggedInUser" class="comment-input">
                    <textarea v-model="newComment" placeholder="发表你的看法..."></textarea>
                    <button class="btn-primary" @click="postComment">发表评论</button>
                </div>
                <div v-else style="text-align: center; padding: 20px; color: var(--text-secondary);">
                    登录后即可参与评论
                </div>
                <div class="comment-list">
                    <div v-for="c in comments" :key="c.id" class="comment-item">
                        <div class="c-header">
                            <div class="c-user">{{ c.authorUsername }} <span class="c-time">{{ formatTime(c.createTime) }}</span></div>
                            <div v-if="isCommentOwner(c)" class="c-actions">
                                <button v-if="!editingCommentId || editingCommentId !== c.id" class="btn-icon" @click="startEditComment(c)" title="编辑">✏️</button>
                                <button class="btn-icon btn-delete" @click="deleteComment(c.id)" title="删除">🗑️</button>
                            </div>
                        </div>
                        <div v-if="editingCommentId === c.id" class="c-edit-form">
                            <textarea v-model="editingContent" class="edit-textarea"></textarea>
                            <div class="edit-actions">
                                <button class="btn-small btn-cancel" @click="cancelEdit">取消</button>
                                <button class="btn-small btn-confirm" @click="saveEdit(c.id)">保存</button>
                            </div>
                        </div>
                        <div v-else class="c-text">{{ c.content }}</div>
                    </div>
                </div>
            </section>
        </div>
    `,
    data: () => ({
        post: null,
        comments: [],
        newComment: '',
        hasLiked: false,
        likeCount: 0,
        aiSummary: null,
        aiLoading: false,
        aiStatus: null, // SUCCESS / FALLBACK / ERROR
        aiSummaryHandler: null,
        editingCommentId: null,
        editingContent: '',
        isEditing: false,
        editingPost: { title: '', content: '' }
    }),
    computed: {
        isOwner() {
            return this.$root.loggedInUser?.id == this.post?.userId;
        }
    },
    methods: {
        formatTime,
        isCommentOwner(comment) {
            return this.$root.loggedInUser?.id == comment.userId;
        },
        startEditPost() {
            this.isEditing = true;
            this.editingPost = { 
                title: this.post.title, 
                content: this.post.content 
            };
        },
        cancelEditPost() {
            this.isEditing = false;
            this.editingPost = { title: '', content: '' };
        },
        async saveEditPost() {
            if (!this.editingPost.title.trim() || !this.editingPost.content.trim()) {
                alert('标题和内容不能为空');
                return;
            }
            try {
                await axios.put(`http://localhost:8080/posts/${this.post.id}`, this.editingPost);
                this.post.title = this.editingPost.title;
                this.post.content = this.editingPost.content;
                this.isEditing = false;
                this.editingPost = { title: '', content: '' };
            } catch (error) {
                alert(error.response?.data?.message || '修改失败');
            }
        },
        startEditComment(comment) {
            this.editingCommentId = comment.id;
            this.editingContent = comment.content;
        },
        cancelEdit() {
            this.editingCommentId = null;
            this.editingContent = '';
        },
        async saveEdit(commentId) {
            if (!this.editingContent.trim()) {
                alert('评论内容不能为空');
                return;
            }
            try {
                await axios.put(`http://localhost:8080/comments/${commentId}`, { 
                    content: this.editingContent 
                });
                this.editingCommentId = null;
                this.editingContent = '';
                this.fetchData();
            } catch (error) {
                alert(error.response?.data?.message || '修改失败');
            }
        },
        async deleteComment(commentId) {
            if (!confirm('确定要删除这条评论吗？')) return;
            try {
                await axios.delete(`http://localhost:8080/comments/${commentId}`);
                this.fetchData();
            } catch (error) {
                alert(error.response?.data?.message || '删除失败');
            }
        },
        async fetchData() {
            try {
                const id = this.$route.params.id;
                const [pRes, lRes, cRes] = await Promise.all([
                    axios.get(`http://localhost:8080/posts/${id}`),
                    axios.get(`http://localhost:8080/posts/${id}/like/status`),
                    axios.get(`http://localhost:8080/posts/${id}/comments`)
                ]);
                this.post = pRes.data.data;
                this.hasLiked = lRes.data.data.liked;
                this.likeCount = lRes.data.data.likeCount;
                this.comments = cRes.data.data;
            } catch (error) {
                console.error('获取数据失败:', error);
            }
        },
        async getAiSummary() {
            if (!this.$root.loggedInUser) return alert('请先登录');

            this.aiLoading = true;
            this.aiSummary = null;
            this.aiStatus = null;

            try {
                const res = await axios.post(`http://localhost:8080/posts/${this.post.id}/ai-summary`);
                console.log('[AI] 接口返回:', res.data);

                if (res.data.code !== 0) {
                    alert(res.data.message || 'AI 任务提交失败');
                    this.aiLoading = false;
                    this.aiStatus = 'ERROR';
                    return;
                }

                const result = res.data.data;

                // 1. 命中缓存：后端直接返回总结字符串
                if (typeof result === 'string' && result && !result.includes('AI助手已开始阅读')) {
                    this.aiSummary = result;
                    this.aiStatus = 'SUCCESS';
                    this.aiLoading = false;
                    return;
                }

                // 2. 已进入异步流程：保持 loading，等待 websocket 推送
                if (typeof result === 'string' && result.includes('AI助手已开始阅读')) {
                    return;
                }

                // 3. 兜底
                this.aiLoading = false;
            } catch (e) {
                alert(e.response?.data?.message || 'AI 任务提交失败');
                this.aiLoading = false;
                this.aiStatus = 'ERROR';
            }
        },
        async toggleLike() {
            if (!this.$root.loggedInUser) return alert('请先登录');
            const id = this.post.id;
            try {
                if (this.hasLiked) {
                    await axios.delete(`http://localhost:8080/posts/${id}/like`);
                    this.likeCount--;
                } else {
                    await axios.post(`http://localhost:8080/posts/${id}/like`);
                    this.likeCount++;
                }
                this.hasLiked = !this.hasLiked;
            } catch (error) {
                console.error('点赞操作失败:', error);
            }
        },
        async postComment() {
            if (!this.newComment.trim()) return;
            try {
                await axios.post(`http://localhost:8080/posts/${this.post.id}/comments`, { content: this.newComment });
                this.newComment = '';
                this.fetchData();
            } catch (error) {
                console.error('评论失败:', error);
            }
        },
        async handleDelete() {
            if (!confirm('确定要删除这篇帖子吗？')) return;
            try {
                await axios.delete(`http://localhost:8080/posts/${this.post.id}`);
                this.$router.push('/');
            } catch (error) {
                console.error('删除失败:', error);
            }
        }
    },
    created() {
        this.fetchData();

        this.aiSummaryHandler = (e) => {
            if (this.post && e.detail.postId == this.post.id) {
                this.aiSummary = e.detail.content;
                this.aiStatus = e.detail.status || 'SUCCESS';
                this.aiLoading = false;
            }
        };

        window.addEventListener('ai-summary-received', this.aiSummaryHandler);
    },
    beforeUnmount() {
        if (this.aiSummaryHandler) {
            window.removeEventListener('ai-summary-received', this.aiSummaryHandler);
        }
    }
};
const routes = [
    { path: '/', component: PostList },
    { path: '/posts/:id', component: PostDetail },
    { path: '/my-posts', component: PostList }
];

const router = VueRouter.createRouter({ history: VueRouter.createWebHashHistory(), routes });

const app = createApp({
    data: () => ({
        loggedInUser: null,
        unreadCount: 0,
        notifications: [],
        isNotificationDropdownVisible: false,
        isLoginModalVisible: false,
        isRegistrationModalVisible: false,
        isCreatePostModalVisible: false,
        loginUser: { username: '', password: '' },
        newUser: { username: '', email: '', password: '', confirmPassword: '' },
        newPost: { title: '', content: '' },
        stompClient: null,
        routerViewKey: 0
    }),
    methods: {
        formatTime,
        connectWebSocket() {
            const socket = new SockJS('http://localhost:8080/ws');
            this.stompClient = Stomp.over(socket);
            const headers = { 'Authorization': `Bearer ${localStorage.getItem('jwt-token')}` };

            this.stompClient.connect(headers, () => {
                // 拿到当前登录用户的 ID
                const userId = this.loggedInUser.id;

                // 1. 订阅该用户的专属通知频道
                this.stompClient.subscribe('/topic/user/' + userId + '/notifications', (msg) => {
                    this.unreadCount++;
                    this.notifications.unshift(JSON.parse(msg.body));
                });

                // 2. 订阅该用户的专属 AI 总结频道
                this.stompClient.subscribe('/topic/user/' + userId + '/ai', (msg) => {
                    const data = JSON.parse(msg.body);
                    console.log('[AI][WebSocket] 收到消息:', data); // 这次绝对能打印出来了！

                    if (data.status === 'SUCCESS' || data.status === 'FALLBACK') {
                        window.dispatchEvent(new CustomEvent('ai-summary-received', { detail: data }));
                        return;
                    }

                    if (data.status === 'ERROR') {
                        window.dispatchEvent(new CustomEvent('ai-summary-received', {
                            detail: {
                                ...data,
                                content: data.content || 'AI生成失败，请稍后重试。'
                            }
                        }));
                        return;
                    }
                });
            }, (error) => {
                console.error('WebSocket 连接失败:', error);
            });
        },
        async handleLogin() {
            if (!this.loginUser.username || !this.loginUser.password) {
                return alert('请填写用户名和密码');
            }
            try {
                const res = await axios.post('http://localhost:8080/users/login', this.loginUser);
                if (res.data.code === 0) {
                    const token = res.data.data;
                    localStorage.setItem('jwt-token', token);
                    axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
                    this.loggedInUser = this.parseJwt(token);
                    this.connectWebSocket();
                    this.isLoginModalVisible = false;
                    this.loginUser = { username: '', password: '' };
                } else {
                    alert(res.data.message);
                }
            } catch (error) {
                alert(error.response?.data?.message || '登录失败');
            }
        },
        parseJwt(token) {
            try {
                return JSON.parse(atob(token.split('.')[1])).claims;
            } catch (e) {
                console.error('Token 解析失败:', e);
                return null;
            }
        },
        logout() {
            if (this.stompClient) {
                this.stompClient.disconnect();
            }
            localStorage.removeItem('jwt-token');
            delete axios.defaults.headers.common['Authorization'];
            location.reload();
        },
        openLoginModal() { this.isLoginModalVisible = true; },
        closeLoginModal() { this.isLoginModalVisible = false; },
        openRegistrationModal() { 
            this.isRegistrationModalVisible = true;
            this.isLoginModalVisible = false;
        },
        closeRegistrationModal() { this.isRegistrationModalVisible = false; },
        switchToRegisterModal() {
            this.isLoginModalVisible = false;
            this.isRegistrationModalVisible = true;
        },
        openCreatePostModal() { 
            if (!this.loggedInUser) return alert('请先登录');
            this.isCreatePostModalVisible = true; 
        },
        closeCreatePostModal() { 
            this.isCreatePostModalVisible = false;
            this.newPost = { title: '', content: '' };
        },
        async handleCreatePost() {
            if (!this.newPost.title.trim() || !this.newPost.content.trim()) {
                return alert('请填写标题和内容');
            }
            try {
                await axios.post('http://localhost:8080/posts', this.newPost);
                this.closeCreatePostModal();
                this.$router.push('/');
                setTimeout(() => location.reload(), 100);
            } catch (error) {
                alert(error.response?.data?.message || '发布失败');
            }
        },
        async registerUser() {
            if (!this.newUser.username || !this.newUser.email || !this.newUser.password) {
                return alert('请填写所有字段');
            }
            if (this.newUser.password !== this.newUser.confirmPassword) {
                return alert('两次密码不一致');
            }
            try {
                await axios.post('http://localhost:8080/users/register', this.newUser);
                alert('注册成功！请登录');
                this.closeRegistrationModal();
                this.openLoginModal();
                this.newUser = { username: '', email: '', password: '', confirmPassword: '' };
            } catch (error) {
                alert(error.response?.data?.message || '注册失败');
            }
        },
        toggleNotificationDropdown() {
            this.isNotificationDropdownVisible = !this.isNotificationDropdownVisible;
        },
        handleNotificationClick(notification) {
            if (!notification.read) {
                notification.read = true;
                this.unreadCount = Math.max(0, this.unreadCount - 1);
                axios.post(`http://localhost:8080/api/notifications/${notification.id}/read`).catch(err => {
                    console.error('标记已读失败:', err);
                });
            }
            this.isNotificationDropdownVisible = false;
        },
        async fetchInitialNotifications() {
            try {
                // 并发请求：获取未读数量 和 获取通知列表
                const [countRes, listRes] = await Promise.all([
                    axios.get('http://localhost:8080/api/notifications/unread-count'),
                    axios.get('http://localhost:8080/api/notifications')
                ]);
                if (countRes.data.code === 0) {
                    this.unreadCount = countRes.data.data;
                }
                if (listRes.data.code === 0) {
                    this.notifications = listRes.data.data;
                }
            } catch (error) {
                console.error('获取初始通知失败:', error);
            }
        },
    },
    created() {
        const token = localStorage.getItem('jwt-token');
        if (token) {
            axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
            this.loggedInUser = this.parseJwt(token);
            this.connectWebSocket();
            this.fetchInitialNotifications();
        }
        
        // 点击外部关闭通知下拉菜单
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.notification-wrapper')) {
                this.isNotificationDropdownVisible = false;
            }
        });
    }
});

app.use(router);
app.mount('#app');