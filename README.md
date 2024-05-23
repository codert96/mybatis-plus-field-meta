# impl com.baomidou.mybatisplus.core.handlers.MetaObjectHandler

<pre>
    @Bean
    public MybatisMetaHandler mybatisMetaHandler(ApplicationContext applicationContext, MybatisMetaContext mybatisMetaContext) {
        return new MybatisMetaHandler(applicationContext, mybatisMetaContext);
    }

    @Bean
    public MybatisMetaContext mybatisMetaContext() {
        User user = new User();
        user.setName("张三");
        user.setUserId(1);
        return new MybatisMetaContext() {
            @Override
            public Object getAuthentication() {

                return user;
            }

            @Override
            public Class<?> getType() {
                return User.class;
            }
        };
    }
  --------------------
@TableName("sys_user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Integer userId;

    @Meta(el = "#requestAttributes.request.getParameter('name')")
    @TableField(fill = FieldFill.INSERT)
    private String name;

    @Meta(el = "#requestAttributes.request.getParameter('deptId')")
    @TableField(fill = FieldFill.INSERT)
    private Long deptId;

    @Meta(el = "@deptService.getById(#root.deptId)?.deptName")
    @TableField(fill = FieldFill.INSERT)
    private Long deptName;

    @Meta(el = "#authentication?.userId")
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
  
    @Meta(value = "name")
    @TableField(fill = FieldFill.INSERT)
    private String createByName;
}

  --------------------
  insert sql = INSERT INTO sys_user ( user_id, name, dept_id, dept_name, create_by, create_by_name ) VALUES (1, null, 123, '测试', 1, '张三')
</pre>
