package plantilla.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    //este metodo nos va a servir para agregar mas usuarios
    //a esto se le llama autenticacion
    @Autowired
    private UserDetailsService userDetailsService;//es una instancia de usuarioService

    @Bean//al declararlo como bean se agrega al contenedor de spring
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void configure(AuthenticationManagerBuilder build) throws Exception {
        //simplemente con haber definido el metodo con autowired ya tenemos disponible
        //el objeto AuthenticationManagerBuilder
        build.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());

    }

    //para seleccionar lo que voy a restringir
    //a esto se le llama autorizacion
@Override
protected void configure(HttpSecurity http) throws Exception {
    http
    .csrf().disable()
    .authorizeRequests()
        .antMatchers("/login", "/recursos/**", "/static/**", "/css/**", "/js/**", "/imagenes/**, /webjars/**").permitAll() // recursos públicos
        .antMatchers("/gestionUsuarios/**").hasRole("ADMIN")
        .anyRequest().authenticated() // todo lo demás requiere login
    .and()
    .formLogin()
        .loginPage("/login").permitAll()
        .defaultSuccessUrl("/menuPrincipal",true) // redirige al menú luego de login exitoso
    .and()
    .logout()
        .logoutSuccessUrl("/login?logout")
        .permitAll()
    .and()
    .exceptionHandling()
        .accessDeniedPage("/errores/403");

}

//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//            .authorizeHttpRequests(auth -> auth
//                .anyRequest().authenticated()
//            )
//            .logout(logout -> logout
//                .logoutUrl("/logout")
//                .logoutSuccessUrl("/login?logout") // Aquí puedes configurar la redirección post logout
//                .invalidateHttpSession(true)
//                .deleteCookies("JSESSIONID")
//            );
//        return http.build();
//    }

}
