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
            }
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
            routerViewKey: 0 // 用于刷新router-view的key
        }
    },
    methods: {
        // fetchPosts() {
        //     axios.get('http://localhost:8080/posts')
        //         .then(response => {
        //             if (response.data.code === 0) {
        //                 this.posts = response.data.data;
        //             } else {
        //                 alert('帖子加载失败: ' + response.data.message);
        //             }
        //         })
        //         .catch(error => {
        //             console.error('获取帖子列表出错:', error);
        //             alert('网络错误，无法加载帖子列表。');
        //         });
        // },
        // formatTime(dateTimeString) {
        //     if (!dateTimeString) return '';
        //     const date = new Date(dateTimeString);
        //     return date.toLocaleString();
        // },
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

                        this.closeLoginModal();
                    } else {
                        alert('登录失败: ' + response.data.message);
                    }
                }).catch(error => alert('登录失败，网络或服务器错误。'));
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
            } else {
                // 如果token解析失败（可能是无效的或过期的），则清理掉
                localStorage.removeItem('jwt-token');
            }
        }
    }
});
app.use(router);

app.mount('#app');